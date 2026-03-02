package pharmacie.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import pharmacie.entity.Fournisseur;

public interface FournisseurRepository extends JpaRepository<Fournisseur, Integer> {
}
