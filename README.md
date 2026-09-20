### Consignes: 
* Ignorez les migrations BDD
* Ne pas modifier les classes qui ont un commentaire: `// WARN: Should not be changed during the exercise
`
* Pour lancer les tests (depuis le sous-répertoire `api`) :
  * unitaires: `mvnw test`
  * integration: `mvnw integration-test`
  * tous: `mvnw verify`

Le projet utilise Java 17. Depuis la racine, les mêmes commandes sont disponibles
avec Task :

* `task test`
* `task test:integration`
* `task verify` (tests, couverture JaCoCo et PMD)
* `task run` (lance l'API avec H2)

Le contrôleur délègue le traitement à `OrderProcessingService`. Les règles de
disponibilité sont isolées par type de produit dans `domain/availability`, tandis
que `ProductService` garde les opérations de sauvegarde et de notification.

### Swagger

Lancer l'application, puis ouvrir [Swagger UI](http://localhost:8080/api/swagger-ui.html).
La documentation OpenAPI peut être désactivée avec `ENABLE_SWAGGER=false`.

### Docker

Construire le JAR puis l'image :

```bash
task build
docker build -t merjane-inventory .
docker run --rm -p 8080:8080 merjane-inventory
```

Ou avec Docker Compose :

```bash
task docker:up
```

L'API est disponible sur `http://localhost:8080/api` et Swagger sur
`http://localhost:8080/api/swagger-ui.html`. Arrêter la stack avec
`task docker:down`.
