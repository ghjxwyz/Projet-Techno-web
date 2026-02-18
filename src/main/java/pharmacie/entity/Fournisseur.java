package pharmacie.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;
import java.util.ArrayList; 
@Entity
@Getter
@Setter
@NoArgsConstructor
@RequiredArgsConstructor
@ToString

public class Fournisseur {
    @Id
    @Basic(optional = false)
    @NonNull
    @Size(min = 1, max = 40)
    @Column(nullable = false)
    private String nom; 
    @Id
    @Basic(optional = false)
    @NonNull
    @Size(min = 1, max = 40)
    @Column(nullable = false, length = 40)
    private String mail;

    @OneToMany (cascade = CascadeType.ALL , mappedBy = "dournisseur")
    @ToString.Exclude
    @JsonIgnoreProperties({"Fournisseur"})
    private ArrayList<Categorie> categories= new ArrayList(); 

    

}
