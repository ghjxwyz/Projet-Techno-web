package pharmacie.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;

import lombok.extern.slf4j.Slf4j;
import pharmacie.dao.MedicamentRepository;
import pharmacie.entity.Categorie;
import pharmacie.entity.Fournisseur;
import pharmacie.entity.Medicament;

@Slf4j
@Service
public class ApprovisionnementService {

    private final MedicamentRepository medicamentDao;

    @Value("${sendgrid.api-key}")
    private String sendGridApiKey;

    @Value("${sendgrid.from-email}")
    private String fromEmail;

    public ApprovisionnementService(MedicamentRepository medicamentDao) {
        this.medicamentDao = medicamentDao;
    }

    /**
     * Détermine les médicaments à réapprovisionner et envoie un mail à chaque fournisseur concerné.
     * Chaque fournisseur reçoit un seul mail récapitulant, catégorie par catégorie,
     * tous les médicaments à réapprovisionner qu'il est susceptible de fournir.
     *
     * @return un résumé de l'opération (nombre de médicaments, fournisseurs contactés, etc.)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> lancerReapprovisionnement() {
        // 1. Trouver les médicaments dont le stock est inférieur au niveau de réappro
        List<Medicament> medicamentsAReappro = medicamentDao.medicamentsAReapprovisionner();

        if (medicamentsAReappro.isEmpty()) {
            log.info("Aucun médicament à réapprovisionner.");
            return Map.of(
                "message", "Aucun médicament ne nécessite de réapprovisionnement.",
                "medicamentsCount", 0,
                "fournisseursContactes", 0
            );
        }

        log.info("{} médicament(s) à réapprovisionner.", medicamentsAReappro.size());

        // 2. Construire la map : Fournisseur -> { Catégorie -> [Médicaments] }
        Map<Fournisseur, Map<Categorie, List<Medicament>>> fournisseurMap = new HashMap<>();

        for (Medicament med : medicamentsAReappro) {
            Categorie categorie = med.getCategorie();
            // Les fournisseurs associés à la catégorie de ce médicament
            List<Fournisseur> fournisseurs = categorie.getFournisseurs();
            for (Fournisseur fournisseur : fournisseurs) {
                fournisseurMap
                    .computeIfAbsent(fournisseur, f -> new LinkedHashMap<>())
                    .computeIfAbsent(categorie, c -> new ArrayList<>())
                    .add(med);
            }
        }

        if (fournisseurMap.isEmpty()) {
            log.warn("Des médicaments sont à réapprovisionner mais aucun fournisseur n'est associé à leurs catégories.");
            List<String> nomsMedicaments = medicamentsAReappro.stream().map(Medicament::getNom).toList();
            return Map.of(
                "message", "Médicaments à réapprovisionner trouvés mais aucun fournisseur associé.",
                "medicamentsCount", medicamentsAReappro.size(),
                "medicaments", nomsMedicaments,
                "fournisseursContactes", 0
            );
        }

        // 3. Envoyer un mail à chaque fournisseur
        int mailsEnvoyes = 0;
        List<String> erreurs = new ArrayList<>();
        List<Map<String, Object>> detailsFournisseurs = new ArrayList<>();

        for (Map.Entry<Fournisseur, Map<Categorie, List<Medicament>>> entry : fournisseurMap.entrySet()) {
            Fournisseur fournisseur = entry.getKey();
            Map<Categorie, List<Medicament>> categoriesMedicaments = entry.getValue();

            if (fournisseur.getEmail() == null || fournisseur.getEmail().isBlank()) {
                String erreur = "Fournisseur " + fournisseur.getNom() + " n'a pas d'adresse email.";
                log.warn(erreur);
                erreurs.add(erreur);
                continue;
            }

            String sujet = "Demande de devis de réapprovisionnement";
            String corps = construireCorpsMail(fournisseur, categoriesMedicaments);

            try {
                envoyerMail(fournisseur.getEmail(), sujet, corps);
                mailsEnvoyes++;
                log.info("Mail envoyé à {} ({})", fournisseur.getNom(), fournisseur.getEmail());

                // Détails pour le résumé
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("fournisseur", fournisseur.getNom());
                detail.put("email", fournisseur.getEmail());
                int nbMeds = categoriesMedicaments.values().stream().mapToInt(List::size).sum();
                detail.put("nbMedicaments", nbMeds);
                detailsFournisseurs.add(detail);
            } catch (IOException e) {
                String erreur = "Erreur lors de l'envoi du mail à " + fournisseur.getNom() + ": " + e.getMessage();
                log.error(erreur, e);
                erreurs.add(erreur);
            }
        }

        Map<String, Object> resultat = new LinkedHashMap<>();
        resultat.put("message", "Réapprovisionnement lancé avec succès.");
        resultat.put("medicamentsAReapprovisionner", medicamentsAReappro.size());
        resultat.put("fournisseursContactes", mailsEnvoyes);
        resultat.put("detailsFournisseurs", detailsFournisseurs);
        if (!erreurs.isEmpty()) {
            resultat.put("erreurs", erreurs);
        }
        return resultat;
    }

    /**
     * Prévisualise les médicaments à réapprovisionner et les fournisseurs concernés,
     * sans envoyer de mail.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> previsualiser() {
        List<Medicament> medicamentsAReappro = medicamentDao.medicamentsAReapprovisionner();

        List<Map<String, Object>> detailsMedicaments = new ArrayList<>();
        Map<String, List<String>> parFournisseur = new LinkedHashMap<>();

        for (Medicament med : medicamentsAReappro) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("nom", med.getNom());
            detail.put("categorie", med.getCategorie().getLibelle());
            detail.put("unitesEnStock", med.getUnitesEnStock());
            detail.put("niveauDeReappro", med.getNiveauDeReappro());
            detail.put("quantiteManquante", Math.max(med.getNiveauDeReappro() - med.getUnitesEnStock(), 0));
            List<String> nomsFournisseurs = med.getCategorie().getFournisseurs().stream()
                .map(Fournisseur::getNom).toList();
            detail.put("fournisseurs", nomsFournisseurs);
            detailsMedicaments.add(detail);

            for (Fournisseur f : med.getCategorie().getFournisseurs()) {
                parFournisseur
                    .computeIfAbsent(f.getNom() + " (" + f.getEmail() + ")", k -> new ArrayList<>())
                    .add(med.getNom());
            }
        }

        Map<String, Object> resultat = new LinkedHashMap<>();
        resultat.put("medicamentsCount", medicamentsAReappro.size());
        resultat.put("medicaments", detailsMedicaments);
        resultat.put("fournisseursCount", parFournisseur.size());
        resultat.put("recapParFournisseur", parFournisseur);
        return resultat;
    }

    /**
     * Construit le corps du mail pour un fournisseur donné,
     * récapitulant catégorie par catégorie les médicaments à réapprovisionner.
     */
    private String construireCorpsMail(Fournisseur fournisseur, Map<Categorie, List<Medicament>> categoriesMedicaments) {
        StringBuilder sb = new StringBuilder();
        sb.append("Bonjour ").append(fournisseur.getNom()).append(",\n\n");
        sb.append("Nous souhaitons passer commande pour les médicaments suivants. ");
        sb.append("Merci de bien vouloir nous transmettre un devis de réapprovisionnement.\n\n");

        for (Map.Entry<Categorie, List<Medicament>> entry : categoriesMedicaments.entrySet()) {
            Categorie categorie = entry.getKey();
            List<Medicament> medicaments = entry.getValue();

            sb.append("=== ").append(categorie.getLibelle()).append(" ===\n");
            for (Medicament med : medicaments) {
                int quantiteManquante = med.getNiveauDeReappro() - med.getUnitesEnStock();
                sb.append(String.format("  - %s (%s) — Stock actuel : %d, Niveau de réappro : %d, Quantité à commander : %d%n",
                    med.getNom(),
                    med.getQuantiteParUnite(),
                    med.getUnitesEnStock(),
                    med.getNiveauDeReappro(),
                    Math.max(quantiteManquante, 0)
                ));
            }
            sb.append("\n");
        }

        sb.append("Cordialement,\n");
        sb.append("La Pharmacie Centrale\n");

        return sb.toString();
    }

    /**
     * Envoie un email via SendGrid.
     */
    private void envoyerMail(String destinataire, String sujet, String corps) throws IOException {
        Email from = new Email(fromEmail);
        Email to = new Email(destinataire);
        Content content = new Content("text/plain", corps);
        Mail mail = new Mail(from, sujet, to, content);

        SendGrid sg = new SendGrid(sendGridApiKey);
        Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint("mail/send");
        request.setBody(mail.build());
        Response response = sg.api(request);

        if (response.getStatusCode() < 200 || response.getStatusCode() >= 300) {
            throw new IOException("SendGrid a retourné le code " + response.getStatusCode() + ": " + response.getBody());
        }
    }
}
