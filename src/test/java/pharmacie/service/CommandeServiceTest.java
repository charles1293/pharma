package pharmacie.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.ConstraintViolationException;
import pharmacie.dao.CommandeRepository;
import pharmacie.dao.LigneRepository;
import pharmacie.dao.MedicamentRepository;

/**
 * Tests unitaires pour la classe CommandeService
 * Basé sur le jeu de données dans "test_data.sql"
 * 
 * Données de test :
 * - Médicament 93 : disponible, 100 en stock, 0 commandé
 * - Médicament 94 : disponible, 100 en stock, 0 commandé
 * - Médicament 95 : disponible, 100 en stock, 0 commandé
 * - Médicament 96 : disponible, 100 en stock, 0 commandé
 * - Médicament 97 : indisponible, 100 en stock, 0 commandé
 * - Médicament 98 : disponible, 26 en stock, 20 commandé
 * - Médicament 99 : disponible, 100 en stock, 0 commandé
 * 
 * - Commande 99999 : déjà envoyée (dispensaire 2COM)
 * - Commande 99998 : pas encore envoyée (dispensaire 2COM), contient médicament 98 (quantité 16)
 * 
 * - Dispensaire 0COM : sans commandes
 * - Dispensaire 2COM : avec commandes
 */
@SpringBootTest
@Transactional // Rollback après chaque test pour ne pas polluer la base
class CommandeServiceTest {

    // Références vers les données de test
    private static final int MEDICAMENT_DISPONIBLE = 93;
    private static final int MEDICAMENT_DISPONIBLE_2 = 94;
    private static final int MEDICAMENT_INDISPONIBLE = 97;
    private static final int MEDICAMENT_PEU_DE_STOCK = 98; // 26 en stock, 20 commandé, reste 6 disponibles
    private static final int COMMANDE_NON_ENVOYEE = 99998;
    private static final int COMMANDE_ENVOYEE = 99999;
    private static final String DISPENSAIRE_AVEC_COMMANDES = "2COM";
    private static final String DISPENSAIRE_SANS_COMMANDES = "0COM";

    @Autowired
    private CommandeService service;

    @Autowired
    private CommandeRepository commandeDao;

    @Autowired
    private MedicamentRepository medicamentDao;

    @Autowired
    private LigneRepository ligneDao;

    // ==================== Tests pour ajouterLigne ====================

    @Test
    void testAjouterLigneReussit() {
        // Given : une commande non envoyée et un médicament disponible
        int quantite = 5;
        
        // When : on ajoute une ligne
        var ligne = service.ajouterLigne(COMMANDE_NON_ENVOYEE, MEDICAMENT_DISPONIBLE, quantite);
        
        // Then : la ligne est créée avec les bonnes valeurs
        assertNotNull(ligne.getId(), "La ligne doit avoir un ID");
        assertEquals(quantite, ligne.getQuantite(), "La quantité doit être correcte");
        assertEquals(MEDICAMENT_DISPONIBLE, ligne.getMedicament().getReference(), "Le médicament doit être correct");
    }

    @Test
    void testAjouterLigneIncrementeUnitesCommandees() {
        // Given : un médicament avec 0 unités commandées
        var medicament = medicamentDao.findById(MEDICAMENT_DISPONIBLE).orElseThrow();
        int unitesCommandeesAvant = medicament.getUnitesCommandees();
        int quantite = 10;
        
        // When : on ajoute une ligne
        service.ajouterLigne(COMMANDE_NON_ENVOYEE, MEDICAMENT_DISPONIBLE, quantite);
        
        // Then : les unités commandées sont incrémentées
        medicament = medicamentDao.findById(MEDICAMENT_DISPONIBLE).orElseThrow();
        assertEquals(unitesCommandeesAvant + quantite, medicament.getUnitesCommandees(),
            "Les unités commandées doivent être incrémentées de la quantité");
    }

    @Test
    void testAjouterLigneMedicamentDejaPresent() {
        // Given : une commande avec déjà le médicament 98 (quantité 16)
        var medicament = medicamentDao.findById(MEDICAMENT_PEU_DE_STOCK).orElseThrow();
        int unitesCommandeesAvant = medicament.getUnitesCommandees();
        int quantiteSupplementaire = 2;
        
        // When : on ajoute encore du même médicament
        var ligne = service.ajouterLigne(COMMANDE_NON_ENVOYEE, MEDICAMENT_PEU_DE_STOCK, quantiteSupplementaire);
        
        // Then : les quantités sont additionnées
        assertEquals(16 + quantiteSupplementaire, ligne.getQuantite(),
            "Les quantités doivent être additionnées");
        
        // Et les unités commandées sont incrémentées
        medicament = medicamentDao.findById(MEDICAMENT_PEU_DE_STOCK).orElseThrow();
        assertEquals(unitesCommandeesAvant + quantiteSupplementaire, medicament.getUnitesCommandees());
    }

    @Test
    void testAjouterLigneCommandeInexistante() {
        // Given : une commande qui n'existe pas
        int commandeInexistante = 99999999;
        
        // When/Then : on obtient une exception
        assertThrows(NoSuchElementException.class, 
            () -> service.ajouterLigne(commandeInexistante, MEDICAMENT_DISPONIBLE, 1),
            "Doit lever une exception si la commande n'existe pas");
    }

    @Test
    void testAjouterLigneMedicamentInexistant() {
        // Given : un médicament qui n'existe pas
        int medicamentInexistant = 99999999;
        
        // When/Then : on obtient une exception
        assertThrows(NoSuchElementException.class,
            () -> service.ajouterLigne(COMMANDE_NON_ENVOYEE, medicamentInexistant, 1),
            "Doit lever une exception si le médicament n'existe pas");
    }

    @Test
    void testAjouterLigneCommandeDejaEnvoyee() {
        // Given : une commande déjà envoyée
        
        // When/Then : on obtient une exception
        assertThrows(IllegalStateException.class,
            () -> service.ajouterLigne(COMMANDE_ENVOYEE, MEDICAMENT_DISPONIBLE, 1),
            "Doit lever une exception si la commande est déjà envoyée");
    }

    @Test
    void testAjouterLigneMedicamentIndisponible() {
        // Given : un médicament indisponible
        
        // When/Then : on obtient une exception
        assertThrows(IllegalStateException.class,
            () -> service.ajouterLigne(COMMANDE_NON_ENVOYEE, MEDICAMENT_INDISPONIBLE, 1),
            "Doit lever une exception si le médicament est indisponible");
    }

    @Test
    void testAjouterLigneStockInsuffisant() {
        // Given : un médicament avec peu de stock (26 en stock, 20 commandé = 6 disponibles)
        // On essaie de commander plus que le stock disponible
        int quantiteTropGrande = 10; // Plus que les 6 disponibles
        
        // When/Then : on obtient une exception
        assertThrows(IllegalStateException.class,
            () -> service.ajouterLigne(COMMANDE_NON_ENVOYEE, MEDICAMENT_PEU_DE_STOCK, quantiteTropGrande),
            "Doit lever une exception si le stock est insuffisant");
    }

    @Test
    void testAjouterLigneQuantiteNonPositive() {
        // Given : une quantité nulle ou négative
        
        // When/Then : on obtient une exception de validation
        assertThrows(ConstraintViolationException.class,
            () -> service.ajouterLigne(COMMANDE_NON_ENVOYEE, MEDICAMENT_DISPONIBLE, 0),
            "Doit lever une exception si la quantité n'est pas positive");
        
        assertThrows(ConstraintViolationException.class,
            () -> service.ajouterLigne(COMMANDE_NON_ENVOYEE, MEDICAMENT_DISPONIBLE, -1),
            "Doit lever une exception si la quantité est négative");
    }

    // ==================== Tests pour supprimerLigne ====================

    @Test
    void testSupprimerLigneReussit() {
        // Given : on ajoute une ligne
        var ligne = service.ajouterLigne(COMMANDE_NON_ENVOYEE, MEDICAMENT_DISPONIBLE, 5);
        int ligneId = ligne.getId();
        
        // When : on supprime la ligne
        service.supprimerLigne(ligneId);
        
        // Then : la ligne n'existe plus
        assertFalse(ligneDao.findById(ligneId).isPresent(), 
            "La ligne doit être supprimée");
    }

    @Test
    void testSupprimerLigneDecrementeUnitesCommandees() {
        // Given : on ajoute une ligne
        int quantite = 5;
        var ligne = service.ajouterLigne(COMMANDE_NON_ENVOYEE, MEDICAMENT_DISPONIBLE, quantite);
        var medicament = medicamentDao.findById(MEDICAMENT_DISPONIBLE).orElseThrow();
        int unitesCommandeesApresAjout = medicament.getUnitesCommandees();
        
        // When : on supprime la ligne
        service.supprimerLigne(ligne.getId());
        
        // Then : les unités commandées sont décrémentées
        medicament = medicamentDao.findById(MEDICAMENT_DISPONIBLE).orElseThrow();
        assertEquals(unitesCommandeesApresAjout - quantite, medicament.getUnitesCommandees(),
            "Les unités commandées doivent être décrémentées de la quantité");
    }

    @Test
    void testSupprimerLigneCommandeDejaEnvoyee() {
        // Given : une ligne d'une commande déjà envoyée
        // La commande 99999 contient la ligne avec médicament 98
        var lignes = ligneDao.findByCommandeNumero(COMMANDE_ENVOYEE);
        assertFalse(lignes.isEmpty(), "La commande envoyée doit avoir des lignes");
        int ligneId = lignes.get(0).getId();
        
        // When/Then : on obtient une exception
        assertThrows(IllegalStateException.class,
            () -> service.supprimerLigne(ligneId),
            "Doit lever une exception si la commande est déjà envoyée");
    }

    @Test
    void testSupprimerLigneInexistante() {
        // Given : une ligne qui n'existe pas
        int ligneInexistante = 99999999;
        
        // When/Then : on obtient une exception
        assertThrows(NoSuchElementException.class,
            () -> service.supprimerLigne(ligneInexistante),
            "Doit lever une exception si la ligne n'existe pas");
    }

    // ==================== Tests pour enregistreExpedition ====================

    @Test
    void testEnregistreExpeditionReussit() {
        // Given : une commande non envoyée
        var commande = commandeDao.findById(COMMANDE_NON_ENVOYEE).orElseThrow();
        assertNull(commande.getEnvoyeele(), "La commande ne doit pas être envoyée initialement");
        
        // When : on enregistre l'expédition
        var commandeExpediee = service.enregistreExpedition(COMMANDE_NON_ENVOYEE);
        
        // Then : la date d'expédition est renseignée
        assertNotNull(commandeExpediee.getEnvoyeele(), "La date d'expédition doit être renseignée");
        assertEquals(LocalDate.now(), commandeExpediee.getEnvoyeele(), 
            "La date d'expédition doit être la date du jour");
    }

    @Test
    void testEnregistreExpeditionDecrementeStock() {
        // Given : on ajoute une ligne avec une quantité connue
        int quantite = 5;
        service.ajouterLigne(COMMANDE_NON_ENVOYEE, MEDICAMENT_DISPONIBLE, quantite);
        var medicament = medicamentDao.findById(MEDICAMENT_DISPONIBLE).orElseThrow();
        int stockAvant = medicament.getUnitesEnStock();
        
        // When : on enregistre l'expédition
        service.enregistreExpedition(COMMANDE_NON_ENVOYEE);
        
        // Then : le stock est décrémenté
        medicament = medicamentDao.findById(MEDICAMENT_DISPONIBLE).orElseThrow();
        assertEquals(stockAvant - quantite, medicament.getUnitesEnStock(),
            "Le stock doit être décrémenté de la quantité expédiée");
    }

    @Test
    void testEnregistreExpeditionDecrementeUnitesCommandees() {
        // Given : on ajoute une ligne avec une quantité connue
        int quantite = 5;
        service.ajouterLigne(COMMANDE_NON_ENVOYEE, MEDICAMENT_DISPONIBLE, quantite);
        var medicament = medicamentDao.findById(MEDICAMENT_DISPONIBLE).orElseThrow();
        int unitesCommandeesAvant = medicament.getUnitesCommandees();
        
        // When : on enregistre l'expédition
        service.enregistreExpedition(COMMANDE_NON_ENVOYEE);
        
        // Then : les unités commandées sont décrémentées
        medicament = medicamentDao.findById(MEDICAMENT_DISPONIBLE).orElseThrow();
        assertEquals(unitesCommandeesAvant - quantite, medicament.getUnitesCommandees(),
            "Les unités commandées doivent être décrémentées de la quantité expédiée");
    }

    @Test
    void testEnregistreExpeditionCommandeInexistante() {
        // Given : une commande qui n'existe pas
        int commandeInexistante = 99999999;
        
        // When/Then : on obtient une exception
        assertThrows(NoSuchElementException.class,
            () -> service.enregistreExpedition(commandeInexistante),
            "Doit lever une exception si la commande n'existe pas");
    }

    @Test
    void testEnregistreExpeditionCommandeDejaEnvoyee() {
        // Given : une commande déjà envoyée
        
        // When/Then : on obtient une exception
        assertThrows(IllegalStateException.class,
            () -> service.enregistreExpedition(COMMANDE_ENVOYEE),
            "Doit lever une exception si la commande est déjà envoyée");
    }

    @Test
    void testEnregistreExpeditionPlusieursLignes() {
        // Given : une commande avec plusieurs lignes
        service.ajouterLigne(COMMANDE_NON_ENVOYEE, MEDICAMENT_DISPONIBLE, 3);
        service.ajouterLigne(COMMANDE_NON_ENVOYEE, MEDICAMENT_DISPONIBLE_2, 7);
        
        var med1 = medicamentDao.findById(MEDICAMENT_DISPONIBLE).orElseThrow();
        var med2 = medicamentDao.findById(MEDICAMENT_DISPONIBLE_2).orElseThrow();
        int stock1Avant = med1.getUnitesEnStock();
        int stock2Avant = med2.getUnitesEnStock();
        
        // When : on enregistre l'expédition
        service.enregistreExpedition(COMMANDE_NON_ENVOYEE);
        
        // Then : les stocks sont décrémentés pour tous les médicaments
        med1 = medicamentDao.findById(MEDICAMENT_DISPONIBLE).orElseThrow();
        med2 = medicamentDao.findById(MEDICAMENT_DISPONIBLE_2).orElseThrow();
        assertEquals(stock1Avant - 3, med1.getUnitesEnStock());
        assertEquals(stock2Avant - 7, med2.getUnitesEnStock());
    }
}
