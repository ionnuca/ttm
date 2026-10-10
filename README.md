# TTM – Tenis de masă

Aplicație web (instalabilă și pe telefon, ca PWA) pentru gestionarea jucătorilor de tenis de masă, a clasamentului și, în etapele următoare, a turneelor.

**Tehnologii:** Java 21 · Spring Boot 3.5 · Vaadin 24 (interfața scrisă în Java) · PostgreSQL 16 · Flyway · Docker

---

## Funcționalități

| Funcționalitate | Guest (nelogat) | Utilizator logat | Manager de turnee | Administrator |
|---|:---:|:---:|:---:|:---:|
| Clasamentul jucătorilor (ordonat după rating, căutare, sortare) | ✅ | ✅ | ✅ | ✅ |
| Mâna de joc în clasament; echipamentul (lemn, fețe forehand/backhand) la click pe jucător | ✅ | ✅ | ✅ | ✅ |
| Coloana „Telefon” în clasament | – | – | – | ✅ |
| Adăugare / editare / ștergere jucători | – | – | – | ✅ |
| Cont nou (înregistrare simplă: nume, prenume, utilizator, parolă) | ✅ | – | – | – |
| „Profilul meu”: date personale, stil și mână de joc, oraș, telefon, echipament, schimbarea parolei | – | ✅ | ✅ | ✅ |
| „Utilizatori”: lista completă, adăugare, editare (rol, blocare, parolă), ștergere | – | – | – | ✅ |
| „Turnee”: lista turneelor, tabelul și meciurile fiecărui turneu | ✅ | ✅ | ✅ | ✅ |
| Înscriere / retragere la un turneu (cât timp înscrierea e deschisă) | – | ✅ | ✅ | ✅ |
| Introducerea rezultatelor (participanții turneului) | – | ✅ | ✅ | ✅ |
| Creare (nume, dată), editare, ștergere turneu; adăugare/scoatere participanți; „Începe turneul” și „Începe etapa 2”; corectarea rezultatelor, inclusiv după încheiere | – | – | ✅ | ✅ |

Interfața se adaptează la telefon: în clasament, numele, stilul de joc și orașul apar într-o singură coloană, iar administratorul editează sau șterge un jucător atingând rândul respectiv. Aplicația se poate adăuga pe ecranul telefonului („Add to Home Screen”).

Reguli:
- Fiecare utilizator este un jucător: la crearea unui cont se creează automat și profilul de jucător, cu ratingul inițial 1000.
- Ștergerea unui jucător șterge și contul lui; ștergerea unui utilizator șterge și profilul de jucător.
- **Managerul de turnee** (rol atribuit de administrator în „Utilizatori”) conduce turneele de la creare până la încheiere, dar nu are acces la pagina „Utilizatori”, nu vede telefoanele și nu editează jucătorii. Ca orice cont, este și jucător, deci se poate înscrie la turnee.
- Administratorul nu își poate șterge propriul cont, nu își poate retrage drepturile și trebuie să rămână mereu cel puțin un administrator activ.
- Ratingul curent și statisticile (victorii / înfrângeri) se calculează automat din turneele încheiate; administratorul stabilește doar **ratingul inițial** al fiecărui jucător.
- Un jucător care a jucat într-un turneu început nu mai poate fi șters, ca rezultatele să rămână complete.

### Turnee

1. **Crearea** (administrator sau manager de turnee): doar numele și data. Înscrierea se deschide imediat.
2. **Înscrierea**: utilizatorii logați se înscriu singuri; organizatorul (administratorul sau managerul de turnee) poate adăuga sau scoate orice jucător.
3. **Începerea** (organizatorul, butonul „Începe turneul”): se alege configurarea – tipul (Round robin), numărul de seturi (best of 3/5/7) și, opțional, **turneu comercial**: taxa de participare și numărul de câștigători, cu împărțirea sumei acumulate (taxa × participanți): 1 câștigător – 100%; 2 – 60% / 40%; 3 – 50% / 30% / 20%. Dialogul arată pe loc suma acumulată și premiile. La confirmare, înscrierea se închide, se formează grupa cu jucătorii ordonați după rating (descrescător) și se generează toate meciurile, pe tururi (fiecare cu fiecare, metoda Berger).
4. **Rezultatele**: le introduc participanții turneului sau organizatorul: scorul la seturi (ex. 3:1) sau **W – victorie tehnică**, când adversarul refuză jocul. După ultimul rezultat, turneul devine „Încheiat”; din acel moment doar organizatorul (administratorul sau managerul de turnee) mai poate corecta.
5. **Tabelul** se actualizează după fiecare rezultat: matrice cu fiecare întâlnire scrisă ca fracție (sus punctele: 2 victorie, 1 înfrângere, 0 înfrângere tehnică; jos scorul la seturi), apoi coloanele *Seturi* (câștigate/pierdute), *Puncte* și *Loc*.

### Turnee „Grupe + finale”

Tipul se alege la „Începe turneul”, împreună cu **numărul de grupe** (minimum 2, cu cel puțin 2 jucători în fiecare).

1. **Etapa 1 — Grupe.** Jucătorii sunt repartizați în grupe în **șerpuială** după rating (A, B, C, C, B, A, A, B…), ca grupele să fie echilibrate. Fiecare grupă joacă Round Robin, cu tabel propriu.
2. **Începe etapa 2** (organizatorul, după ultimul rezultat din grupe): se alege câți jucători din fiecare grupă se califică. Primii N din fiecare grupă joacă în **Finala 1**, ceilalți în **Finala 2**. Dialogul arată componența finalelor înainte de confirmare.
3. **Etapa 2 — Finale.** Ambele finale se joacă Round Robin. Jucătorii care s-au întâlnit deja în aceeași grupă **nu mai joacă** între ei: rezultatul din etapa 1 se preia în tabelul finalei (fiecare meci contează o singură dată la rating). După pornirea etapei 2, rezultatele din grupe nu mai pot fi modificate, pentru că au stabilit calificarea.
4. **Premii (turneu comercial):** câștigătorul Finalei 2 primește cât **taxa de participare**; restul sumei acumulate se împarte între premiații Finalei 1, ca la turneul comercial obișnuit (100% / 60–40% / 50–30–20%).
5. Turneul se încheie după ultimul rezultat din finale; ratingul Elo se calculează pe toate meciurile din ambele etape.

### Ratingul (Elo)

Ratingul se calculează după formula Elo, la încheierea fiecărui turneu:

```
E_A  = 1 / (1 + 10^((R_B − R_A) / 400))     probabilitatea așteptată ca A să câștige
R_A' = R_A + K × (S_A − E_A)                S_A = 1 victorie, 0 înfrângere
```

- **K = 40** până la 30 de meciuri cu rating jucate, apoi **K = 20** (ca la FIDE);
- o diferență de rating mai mare de **400** de puncte se socotește ca 400;
- toate meciurile unui turneu se calculează din ratingurile **de dinaintea turneului**; schimbarea totală se rotunjește o singură dată (0,5 departe de zero);
- **victoriile tehnice (W) nu modifică ratingul**, dar contează la victorii/înfrângeri;
- scorul la seturi nu contează, doar victoria.

Ratingul nu se modifică incremental, ci se **reconstruiește**: din ratingul inițial al fiecărui jucător se aplică, în ordine cronologică (data turneului), toate turneele încheiate. Astfel, când administratorul corectează un rezultat într-un turneu încheiat, șterge un rezultat sau un turneu, ori schimbă ratingul inițial al unui jucător, se recalculează corect și toate turneele de după. Recalcularea are loc și la pornirea aplicației.

Unde se vede: coloana *Rating* din clasament; în tabelul unui turneu încheiat, sub fiecare nume, ratingul înainte → după; în lista meciurilor, schimbarea din fiecare meci; în „Profilul meu”, *Evoluția ratingului* pe turnee.

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

Profilul `dev` încarcă 10 jucători demonstrativi, trei turnee (unul încheiat, cu rating calculat; unul comercial în desfășurare; unul cu înscrierea deschisă) și creează conturile:

| Utilizator | Parolă | Rol |
|---|---|---|
| `admin` | `admin12345` | Administrator |
| `jucator` | `jucator123` | Utilizator (legat de jucătorul „Popescu Ion”) |
| `manager` | `manager123` | Manager de turnee (legat de jucătorul „Lungu Victor”) |

În modul de dezvoltare, modificările din clasele Java se aplică după repornirea aplicației.

### PostgreSQL fără Docker

Dacă aveți PostgreSQL instalat local, creați rolul și bazele de date cu:

```powershell
psql -U postgres -f database/create_database.sql
```

---

## Baza de date

- **Scripturile de creare a tabelelor:** `src/main/resources/db/migration/` (`V1__jucatori_si_utilizatori.sql`, `V2__jucator_mana_si_echipament.sql`, `V3__turnee.sql`, `V4__rating_elo.sql`, `V5__grupe_si_finale.sql`, `V6__rol_manager_turnee.sql`). Le aplică automat **Flyway** la pornirea aplicației, în ordinea versiunilor; nu e nevoie să le rulați manual.
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
play_style    ATTACK / DEFENCE          role           USER / TOURNAMENT_MANAGER / ADMIN
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
                                                            rating_delta_a / _b  schimbarea Elo

player (V4): initial_rating — ratingul de pornire; rating, wins, losses — calculate
rating_history: player_id, tournament_id, rating_before, rating_after, k_factor, rated_matches

tournament (V5): format ROUND_ROBIN / GROUPS_FINALS; stage GROUPS / FINALS; group_count; qualifiers_per_group
tournament_group: tournament_id, stage (GROUPS / FINALS), position, name ("Grupa A", "Finala 1"…)
tournament_group_member: group_id, participant_id, seed (poziția în grupă)
tournament_match.group_id: grupa sau finala din care face parte meciul
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
| `APP_ADMIN_FIRST_NAME` / `APP_ADMIN_LAST_NAME` | *(gol)* | Opțional: numele administratorului; cu ambele completate primește și profil de jucător |
| `APP_REGISTRATION_ENABLED` | `true` | Permite crearea de conturi din pagina „Cont nou” |
| `SPRING_PROFILES_ACTIVE` | *(gol)* | `dev` = dezvoltare locală, `demo` = date demonstrative complete, `seed` = doar 10 jucători de test (într-o bază goală) |
| `JAVA_OPTS` | `-Xmx1g` (producție) | Opțiunile JVM, de ex. memoria maximă |
| `PORT` | `8080` | Portul HTTP |

Administratorul se creează doar dacă nu există încă niciun administrator activ, deci schimbarea ulterioară a `APP_ADMIN_PASSWORD` nu modifică parola; aceasta se schimbă din „Profilul meu”.

---

## Producție (VPS)

Configurația de producție e în `deploy/`: PostgreSQL (fără port public), aplicația și **Caddy**, care obține automat certificatul HTTPS de la Let's Encrypt. Merge pe orice VPS cu Ubuntu 22.04/24.04 și minimum 2 GB RAM (recomandat 4 GB), pe procesor x86 sau ARM.

**Publicarea automată.** Workflow-ul `Publicare` (`.github/workflows/deploy.yml`) rulează după fiecare CI reușit pe `main`:
1. construiește imaginea de producție (amd64 + arm64);
2. pornește configurația de producție completă și verifică HTTPS-ul, crearea administratorului, jucătorii de test și backup-ul;
3. publică imaginea în `ghcr.io/ionnuca/ttm`;
4. dacă serverul e configurat, o instalează pe server prin SSH și așteaptă pornirea aplicației.

**Prima instalare** (o singură dată):
1. Pe server, ca utilizator cu drept de `sudo`:
   ```bash
   curl -fsSLO https://raw.githubusercontent.com/ionnuca/ttm/main/deploy/server-setup.sh
   sudo bash server-setup.sh
   ```
   Scriptul instalează Docker, firewall-ul (22, 80, 443), swap, actualizările automate de securitate și backup-ul zilnic, apoi întreabă domeniul, emailul pentru HTTPS, utilizatorul, numele și parola administratorului și dacă se adaugă jucătorii de test. Rezultatul e `/opt/ttm/.env` (parola bazei de date se generează aleatoriu).
2. **DNS:** o înregistrare de tip **A** pentru domeniu (de ex. `turnee.exemplu.md`) spre IP-ul serverului.
3. **GitHub** → *Settings → Secrets and variables → Actions* → trei secrete afișate de script la final: `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`.
4. **GitHub** → *Actions → Publicare → Run workflow*. După 1–2 minute aplicația e la `https://<domeniu>`.

De aici înainte, orice modificare ajunsă pe `main` se publică singură.

**Pe server** (`/opt/ttm`):

| Ce | Comanda |
|---|---|
| Starea containerelor | `docker compose ps` |
| Jurnalul aplicației | `docker compose logs -f app` |
| Repornire | `docker compose restart app` |
| Backup manual | `./backup.sh` (automat zilnic la 03:30, se păstrează 14 zile în `backups/`) |
| Restaurare | `docker compose exec -T db pg_restore -U ttm -d ttm --clean --if-exists < backups/<fișier>.dump` |

Copiile de rezervă stau pe același server. Pentru siguranță, descărcați periodic una pe alt calculator: `scp <utilizator>@<server>:/opt/ttm/backups/<fișier>.dump .`

---

## Ramuri și release

| Ramura | Rol | Ce se întâmplă la push |
|---|---|---|
| `develop` | Dezvoltarea de zi cu zi: aici ajung toate modificările | CI (teste + build de producție); **nu** se publică pe server |
| `main` | Doar versiunile lansate în producție | CI, apoi workflow-ul `Publicare` instalează automat versiunea pe server |

**Release în producție** = aducerea lui `develop` în `main`, după ce CI e verde pe `develop`:

- din GitHub: *Pull requests → New pull request*, `base: main` ← `compare: develop`, apoi *Merge pull request*;
- sau local:
  ```bash
  git checkout main && git pull
  git merge --no-ff develop -m "Release: <ce conține>"
  git push
  git checkout develop
  ```

La câteva minute după push pe `main`, versiunea nouă rulează pe server.

**Versiuni.** Pe `develop` versiunea din `pom.xml` e următoarea versiune în lucru, cu sufixul `-SNAPSHOT` (ex. `1.1.0-SNAPSHOT`). La release:
1. pe `develop`, versiunea devine cea finală (`1.1.0`), commit „Release 1.1.0”;
2. `develop` se aduce în `main`; workflow-ul `Publicare` creează singur tag-ul `v1.1.0` și un GitHub Release cu lista modificărilor;
3. pe `develop`, versiunea trece la următoarea (`1.2.0-SNAPSHOT`).

Versiunea rulată apare în meniul lateral al aplicației („Versiunea 1.1.0”), iar imaginea Docker e publicată și cu eticheta versiunii (`ghcr.io/ionnuca/ttm:1.1.0`). Pentru o funcționalitate mai mare se poate lucra și pe o ramură separată din `develop` (`feature/...`), adusă apoi în `develop`.

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
│   ├── tournament/              Tournament, TournamentParticipant, TournamentMatch, PrizeDistribution …
│   └── rating/                  RatingHistory
├── repository/                  interfețele Spring Data pentru acces la baza de date
├── service/                     logica de business (tranzacții, reguli, permisiuni)
│   ├── player/                  jucători și clasament
│   ├── user/                    conturi, înregistrare
│   ├── tournament/              turnee, generarea meciurilor (RoundRobinScheduler),
│   │                            tabelul și departajarea (StandingsCalculator)
│   └── rating/                  formulele Elo, calculul pe turneu, recalcularea cronologică
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
