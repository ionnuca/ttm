# TTM – Tenis de masă

Aplicație web (instalabilă și pe telefon, ca PWA) pentru gestionarea jucătorilor de tenis de masă, a clasamentului și, în etapele următoare, a turneelor.

**Tehnologii:** Java 21 · Spring Boot 3.5 · Vaadin 24 (interfața scrisă în Java) · PostgreSQL 16 · Flyway · Docker

---

## Funcționalități

| Funcționalitate | Guest (nelogat) | Utilizator logat | Administrator |
|---|:---:|:---:|:---:|
| Clasamentul jucătorilor (ordonat după rating, căutare, sortare) | ✅ | ✅ | ✅ |
| Mâna de joc în clasament; echipamentul (lemn, fețe forehand/backhand) la click pe jucător | ✅ | ✅ | ✅ |
| Coloana „Telefon” în clasament | – | – | ✅ |
| Adăugare / editare / ștergere jucători | – | – | ✅ |
| Cont nou (înregistrare simplă: nume, prenume, utilizator, parolă) | ✅ | – | – |
| „Profilul meu”: date personale, stil și mână de joc, oraș, telefon, echipament, schimbarea parolei | – | ✅ | ✅ |
| „Utilizatori”: lista completă, adăugare, editare (rol, blocare, parolă), ștergere | – | – | ✅ |
| „Turnee”: lista turneelor, tabelul și meciurile fiecărui turneu | ✅ | ✅ | ✅ |
| Înscriere / retragere la un turneu (cât timp înscrierea e deschisă) | – | ✅ | ✅ |
| Introducerea rezultatelor (participanții turneului) | – | ✅ | ✅ |
| Creare (nume, dată), editare, ștergere turneu; adăugare/scoatere participanți; „Începe turneul” cu alegerea configurării; ștergerea unui rezultat | – | – | ✅ |

Interfața se adaptează la telefon: în clasament, numele, stilul de joc și orașul apar într-o singură coloană, iar administratorul editează sau șterge un jucător atingând rândul respectiv. Aplicația se poate adăuga pe ecranul telefonului („Add to Home Screen”).

Reguli:
- Fiecare utilizator este un jucător: la crearea unui cont se creează automat și profilul de jucător, cu ratingul inițial 1000.
- Ștergerea unui jucător șterge și contul lui; ștergerea unui utilizator șterge și profilul de jucător.
- Administratorul nu își poate șterge propriul cont, nu își poate retrage drepturile și trebuie să rămână mereu cel puțin un administrator activ.
- Ratingul și statisticile (victorii / înfrângeri) le modifică doar administratorul; ulterior vor fi calculate automat din rezultatele meciurilor.
- Un jucător care a jucat într-un turneu început nu mai poate fi șters, ca rezultatele să rămână complete.

### Turnee (Round Robin)

1. **Crearea** (administrator): doar numele și data. Înscrierea se deschide imediat.
2. **Înscrierea**: utilizatorii logați se înscriu singuri; administratorul poate adăuga sau scoate orice jucător.
3. **Începerea** (administrator, butonul „Începe turneul”): se alege configurarea – tipul (Round robin), numărul de seturi (best of 3/5/7) și, opțional, **turneu comercial**: taxa de participare și numărul de câștigători, cu împărțirea sumei acumulate (taxa × participanți): 1 câștigător – 100%; 2 – 60% / 40%; 3 – 50% / 30% / 20%. Dialogul arată pe loc suma acumulată și premiile. La confirmare, înscrierea se închide, se formează grupa cu jucătorii ordonați după rating (descrescător) și se generează toate meciurile, pe tururi (fiecare cu fiecare, metoda Berger).
4. **Rezultatele**: le introduc participanții turneului sau administratorul: scorul la seturi (ex. 3:1) sau **W – victorie tehnică**, când adversarul refuză jocul. După ultimul rezultat, turneul devine „Încheiat”; din acel moment doar administratorul mai poate corecta.
5. **Tabelul** se actualizează după fiecare rezultat: matrice cu fiecare întâlnire scrisă ca fracție (sus punctele: 2 victorie, 1 înfrângere, 0 înfrângere tehnică; jos scorul la seturi), apoi coloanele *Seturi* (câștigate/pierdute), *Puncte* și *Loc*.

Departajarea la egalitate de puncte: punctele din meciurile directe dintre jucătorii la egalitate, apoi raportul seturilor din aceste meciuri, apoi raportul seturilor din toate meciurile, apoi poziția în grupă. O victorie tehnică se socotește la seturi ca victorie la scor alb (3:0 la best of 5).

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

Profilul `dev` încarcă 10 jucători demonstrativi, două turnee (unul comercial în desfășurare, unul cu înscrierea deschisă) și creează conturile:

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

- **Scripturile de creare a tabelelor:** `src/main/resources/db/migration/` (`V1__jucatori_si_utilizatori.sql`, `V2__jucator_mana_si_echipament.sql`, `V3__turnee.sql`). Le aplică automat **Flyway** la pornirea aplicației, în ordinea versiunilor; nu e nevoie să le rulați manual.
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
play_hand     RIGHT / LEFT
city                                    enabled
phone         (vizibil doar adminului)  player_id      FK → player.id (unic)
rating        implicit 1000
wins, losses
blade, forehand_rubber, backhand_rubber   (echipament)

tournament                   tournament_participant         tournament_match
─────────────────────        ──────────────────────────     ─────────────────────────────
id           PK              id             PK              id             PK
name, tournament_date        tournament_id  FK → tournament tournament_id  FK → tournament
format       ROUND_ROBIN     player_id      FK → player     round_no       turul
best_of      3 / 5 / 7       seed           poziția în grupă participant_a / participant_b
status       REGISTRATION /  seed_rating    rating la start sets_a, sets_b scorul la seturi
             IN_PROGRESS /                                  outcome        NORMAL / WALKOVER
             FINISHED                                       winner_id      FK → participant
commercial, winners_count, entry_fee                        recorded_by, recorded_at
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
├── TtmApplication.java          pornirea aplicației, configurarea PWA
├── common/                      excepții și utilitare comune
├── config/                      administratorul inițial, datele demonstrative
├── security/                    Spring Security: configurare, încărcarea conturilor
├── model/                       entitățile JPA și enumerările
│   ├── player/                  Player, PlayStyle, PlayHand
│   ├── user/                    AppUser, Role
│   └── tournament/              Tournament, TournamentParticipant, TournamentMatch, PrizeDistribution …
├── repository/                  interfețele Spring Data pentru acces la baza de date
├── service/                     logica de business (tranzacții, reguli, permisiuni)
│   ├── player/                  jucători și clasament
│   ├── user/                    conturi, înregistrare
│   └── tournament/              turnee, generarea meciurilor (RoundRobinScheduler),
│                                tabelul și departajarea (StandingsCalculator)
└── ui/                          paginile Vaadin
    ├── layout/                  structura comună (meniu, bara de sus)
    ├── components/              componente reutilizabile (notificări, etichete, adaptare la telefon)
    ├── player/                  clasamentul jucătorilor
    ├── user/                    administrarea utilizatorilor
    ├── account/                 autentificare, cont nou, profilul meu
    └── tournament/              lista turneelor, pagina turneului, tabelul-matrice, rezultate
src/main/resources/
├── application.properties     configurarea implicită
├── application-dev.properties profilul de dezvoltare
└── db/migration/              scripturile Flyway
database/                      scripturi pentru crearea bazei de date
```

Securitatea e aplicată pe două niveluri: paginile sunt protejate prin adnotări (`@AnonymousAllowed`, `@PermitAll`, `@RolesAllowed("ADMIN")`), iar serviciile verifică din nou rolul la fiecare operație de modificare (`@PreAuthorize`).
