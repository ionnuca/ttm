# TTM – Tenis de masă

Aplicație web (instalabilă și pe telefon, ca PWA) pentru gestionarea jucătorilor de tenis de masă, a clasamentului și, în etapele următoare, a turneelor.

**Tehnologii:** Java 21 · Spring Boot 3.5 · Vaadin 24 (interfața scrisă în Java) · PostgreSQL 16 · Flyway · Docker

---

## Funcționalități (etapa 1)

| Funcționalitate | Guest (nelogat) | Utilizator logat | Administrator |
|---|:---:|:---:|:---:|
| Clasamentul jucătorilor (ordonat după rating, căutare, sortare) | ✅ | ✅ | ✅ |
| Coloana „Telefon” în clasament | – | – | ✅ |
| Adăugare / editare / ștergere jucători | – | – | ✅ |
| Cont nou (înregistrare simplă: nume, prenume, utilizator, parolă) | ✅ | – | – |
| „Profilul meu”: date personale, stil de joc, oraș, telefon, schimbarea parolei | – | ✅ | ✅ |
| „Utilizatori”: lista completă, adăugare, editare (rol, blocare, parolă), ștergere | – | – | ✅ |

Interfața se adaptează la telefon: în clasament, numele, stilul de joc și orașul apar într-o singură coloană, iar administratorul editează sau șterge un jucător atingând rândul respectiv. Aplicația se poate adăuga pe ecranul telefonului („Add to Home Screen”).

Reguli:
- Fiecare utilizator este un jucător: la crearea unui cont se creează automat și profilul de jucător, cu ratingul inițial 1000.
- Ștergerea unui jucător șterge și contul lui; ștergerea unui utilizator șterge și profilul de jucător.
- Administratorul nu își poate șterge propriul cont, nu își poate retrage drepturile și trebuie să rămână mereu cel puțin un administrator activ.
- Ratingul și statisticile (victorii / înfrângeri) le modifică doar administratorul; ulterior vor fi calculate automat din rezultatele meciurilor.

---

## Rulare locală

### Varianta 1 – totul în Docker (cel mai simplu)

Necesar: [Docker Desktop](https://www.docker.com/products/docker-desktop/).

```powershell
git clone https://github.com/ionnuca/ttm.git
cd ttm
copy .env.example .env        # pe Linux/macOS: cp .env.example .env
```

În `.env` setați `APP_ADMIN_PASSWORD` și, pentru date de test, `SPRING_PROFILES_ACTIVE=demo`. Apoi:

```powershell
docker compose up -d --build
```

Primul build durează câteva minute (descarcă dependențele Maven și Node). Aplicația pornește la **http://localhost:8080**.

```powershell
docker compose logs -f app    # loguri
docker compose down           # oprire (datele rămân în volumul ttm-pgdata)
docker compose down -v        # oprire + ștergerea completă a bazei de date
```

### Varianta 2 – dezvoltare din IDE (IntelliJ IDEA)

Necesar: JDK 21, Maven 3.9+ (inclus în IntelliJ), Docker Desktop pentru baza de date.

1. Porniți doar baza de date:
   ```powershell
   docker compose up -d db
   ```
2. Deschideți proiectul în IntelliJ (*File → Open →* folderul `ttm`); se importă automat ca proiect Maven.
3. Rulați `TtmApplication` cu profilul **dev**: *Run → Edit Configurations → Active profiles: `dev`*.
   Din linia de comandă:
   ```powershell
   mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
   ```
4. Deschideți **http://localhost:8080**.

Profilul `dev` încarcă 10 jucători demonstrativi și creează conturile:

| Utilizator | Parolă | Rol |
|---|---|---|
| `admin` | `admin12345` | Administrator |
| `jucator` | `jucator123` | Utilizator (legat de jucătorul „Popescu Ion”) |

În modul de dezvoltare, modificările din clasele Java se aplică după repornirea aplicației.

### PostgreSQL fără Docker

Dacă aveți PostgreSQL instalat local, creați rolul și bazele de date cu:

```powershell
psql -U postgres -f database/create_database.sql
```

---

## Baza de date

- **Scripturile de creare a tabelelor:** `src/main/resources/db/migration/` (de exemplu `V1__jucatori_si_utilizatori.sql`). Le aplică automat **Flyway** la pornirea aplicației, în ordinea versiunilor; nu e nevoie să le rulați manual.
- **Modificări de schemă:** nu se editează niciodată un script deja aplicat. Se adaugă unul nou: `V2__descriere.sql`, `V3__...` etc.
- **Crearea bazei de date și a utilizatorului:** `database/create_database.sql` (fără Docker) sau automat de `docker-compose.yml`.
- **Baza pentru teste:** `ttm_test`, creată de `database/init/01-create-test-db.sql` la prima pornire a containerului.
- Hibernate doar **validează** schema (`ddl-auto=validate`); nu creează și nu modifică tabele.

Schema actuală:

```
player                                  app_user
──────────────────────────              ──────────────────────────
id            PK                        id             PK
first_name    nume                      username       unic, litere mici
last_name     prenume                   password_hash  BCrypt
play_style    ATTACK / DEFENCE          role           USER / ADMIN
city                                    enabled
phone         (vizibil doar adminului)  player_id      FK → player.id (unic)
rating        implicit 1000
wins, losses
```

---

## Configurare

Toate setările se pot da prin variabile de mediu (sau în `.env` pentru Docker Compose):

| Variabilă | Implicit | Descriere |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/ttm` | Adresa bazei de date |
| `DB_USER` / `DB_PASSWORD` | `ttm` / `ttm` | Credențialele bazei de date |
| `APP_ADMIN_USERNAME` | `admin` | Administratorul creat la prima pornire |
| `APP_ADMIN_PASSWORD` | *(gol)* | Parola lui; dacă lipsește, se generează și se afișează **o singură dată** în log |
| `APP_REGISTRATION_ENABLED` | `true` | Permite crearea de conturi din pagina „Cont nou” |
| `SPRING_PROFILES_ACTIVE` | *(gol)* | `dev` = dezvoltare locală, `demo` = doar date demonstrative |
| `PORT` | `8080` | Portul HTTP |

Administratorul se creează doar dacă nu există încă niciun administrator activ, deci schimbarea ulterioară a `APP_ADMIN_PASSWORD` nu modifică parola; aceasta se schimbă din „Profilul meu”.

---

## Teste și build

```powershell
mvn clean package                      # compilare + JAR (nu are nevoie de baza de date)
docker compose up -d db                # pornește PostgreSQL; testele folosesc baza ttm_test
mvn clean verify                       # în plus, testele de integrare (*IT.java) pe baza de date
mvn -Pproduction clean package         # JAR de producție: target/ttm-0.1.0-SNAPSHOT.jar
```

Testele de integrare se află în fișierele `*IT.java` și rulează doar la `mvn verify`. Dacă la `verify` apare eroarea `Connection to localhost:5432 refused`, baza de date nu e pornită: rulați `docker compose up -d db`.

La fiecare push, GitHub Actions (`.github/workflows/ci.yml`) rulează testele și build-ul de producție. Workflow-ul manual **Capturi interfață** pornește aplicația cu date demonstrative și salvează capturi de ecran ale tuturor paginilor, pe desktop și pe telefon.

---

## Structura proiectului

```
src/main/java/md/ttm/
├── TtmApplication.java        pornirea aplicației, configurarea PWA
├── common/                    excepții și utilitare comune
├── config/                    administratorul inițial, datele demonstrative
├── player/                    jucătorul: entitate, repository, serviciu, clasament
├── user/                      conturi: entitate, roluri, serviciu, formulare
├── security/                  Spring Security: configurare, încărcarea conturilor
└── ui/                        paginile Vaadin (clasament, utilizatori, profil, login, cont nou)
src/main/resources/
├── application.properties     configurarea implicită
├── application-dev.properties profilul de dezvoltare
└── db/migration/              scripturile Flyway
database/                      scripturi pentru crearea bazei de date
```

Securitatea e aplicată pe două niveluri: paginile sunt protejate prin adnotări (`@AnonymousAllowed`, `@PermitAll`, `@RolesAllowed("ADMIN")`), iar serviciile verifică din nou rolul la fiecare operație de modificare (`@PreAuthorize`).
