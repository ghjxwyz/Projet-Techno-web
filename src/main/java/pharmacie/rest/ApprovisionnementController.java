package pharmacie.rest;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import pharmacie.service.ApprovisionnementService;

@RestController
@RequestMapping("/api/approvisionnement")
@Tag(name = "Approvisionnement", description = "Service de réapprovisionnement des médicaments")
public class ApprovisionnementController {

    private final ApprovisionnementService approvisionnementService;

    public ApprovisionnementController(ApprovisionnementService approvisionnementService) {
        this.approvisionnementService = approvisionnementService;
    }

    @PostMapping("/lancer")
    @Operation(summary = "Lancer le réapprovisionnement",
        description = "Détermine les médicaments à réapprovisionner et envoie un mail de demande de devis à chaque fournisseur concerné.")
    public ResponseEntity<Map<String, Object>> lancerReapprovisionnement() {
        Map<String, Object> resultat = approvisionnementService.lancerReapprovisionnement();
        return ResponseEntity.ok(resultat);
    }

    @GetMapping("/preview")
    @Operation(summary = "Prévisualiser les médicaments à réapprovisionner",
        description = "Retourne la liste des médicaments dont le stock est inférieur au niveau de réapprovisionnement, sans envoyer de mail.")
    public ResponseEntity<Map<String, Object>> previsualiser() {
        Map<String, Object> resultat = approvisionnementService.previsualiser();
        return ResponseEntity.ok(resultat);
    }
}
