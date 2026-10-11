package md.ttm.ui.help;

import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import md.ttm.ui.layout.MainLayout;

/**
 * Ghidul de utilizare, public: cum se folosește aplicația, cum se organizează un turneu,
 * cum se calculează clasamentul în grupă și ratingul.
 */
@Route(value = "ajutor", layout = MainLayout.class)
@PageTitle("Ghid de utilizare | TTM")
@AnonymousAllowed
public class HelpView extends VerticalLayout {

    public HelpView() {
        setMaxWidth("48rem");
        add(new Html(CONTENT));
    }

    private static final String CONTENT = """
            <div class="ttm-help" style="line-height:1.6;max-width:100%">
            <style>
              .ttm-help h2 { margin: 0 0 var(--lumo-space-s); }
              .ttm-help h3 { margin: var(--lumo-space-xl) 0 var(--lumo-space-s); }
              .ttm-help table { border-collapse: collapse; width: 100%; margin: var(--lumo-space-s) 0; font-size: var(--lumo-font-size-s); }
              .ttm-help th, .ttm-help td { border: 1px solid var(--lumo-contrast-20pct); padding: var(--lumo-space-xs) var(--lumo-space-s); text-align: left; vertical-align: top; }
              .ttm-help th { background: var(--lumo-contrast-5pct); }
              .ttm-help .formula { font-family: var(--lumo-font-family-monospace, monospace); background: var(--lumo-contrast-5pct);
                                   border-radius: var(--lumo-border-radius-m); padding: var(--lumo-space-s) var(--lumo-space-m); overflow-x: auto; white-space: pre; }
              .ttm-help .note { color: var(--lumo-secondary-text-color); font-size: var(--lumo-font-size-s); }
            </style>

            <h2>Ghid de utilizare</h2>
            <p>TTM ține evidența jucătorilor de tenis de masă, a turneelor, a meciurilor și a ratingului fiecărui jucător.
            Pagina principală arată ultimele meciuri; clasamentul general e la <b>Jucători</b>.</p>

            <h3>1. Conturi și roluri</h3>
            <table>
              <tr><th>Rol</th><th>Ce poate face</th></tr>
              <tr><td>Vizitator (fără cont)</td><td>Vede meciurile, clasamentul, paginile jucătorilor și turneele.</td></tr>
              <tr><td>Utilizator</td><td>Își face cont din „Cont nou” (fiecare cont este și jucător, cu ratingul inițial 1000).
                  Se înscrie la turnee, introduce rezultatele propriilor meciuri, își completează profilul și poza.</td></tr>
              <tr><td>Manager de turnee</td><td>Creează și conduce turneele: înscrieri, start, etapa 2, încheiere, corectarea oricărui rezultat.</td></tr>
              <tr><td>Administrator</td><td>Tot ce face managerul, plus utilizatorii și rolurile, jucătorii (inclusiv ratingul inițial) și backup-ul datelor.</td></tr>
            </table>

            <h3>2. Cum se organizează un turneu</h3>
            <ol>
              <li><b>Crearea.</b> Managerul (sau administratorul) apasă „Turneu nou” la <b>Turnee</b> și dă doar numele și data. Înscrierea se deschide imediat.</li>
              <li><b>Înscrierea.</b> Jucătorii cu cont apasă „Mă înscriu” pe pagina turneului; organizatorul poate adăuga sau scoate pe oricine.</li>
              <li><b>Începerea.</b> „Începe turneul” închide înscrierea. Atunci se alege:
                <ul>
                  <li>tipul: <b>Round robin</b> sau <b>Grupe + finale</b> (cu numărul de grupe);</li>
                  <li>numărul de seturi: best of 3, 5 sau 7 (câștigă primul la 2, 3 sau 4 seturi);</li>
                  <li>opțional, <b>turneu comercial</b>: taxa de participare și numărul de premiați.</li>
                </ul>
                Aplicația formează grupele și generează toate meciurile, pe tururi.</li>
              <li><b>Rezultatele.</b> Fiecare meci primește scorul la seturi (ex. 3:1) sau <b>W – victorie tehnică</b>, când adversarul nu joacă.
                  Rezultatele le introduc cei doi jucători sau organizatorul.</li>
              <li><b>Încheierea.</b> După ultimul rezultat turneul devine „Încheiat” și ratingul se recalculează.
                  Organizatorul îl poate încheia și manual: meciurile fără rezultat rămân „nejucate” și nu contează nici la clasament, nici la rating.
                  Rezultatele pot fi corectate și după încheiere; ratingul se recalculează atunci automat.</li>
            </ol>

            <h3>3. Formatele de turneu</h3>
            <p><b>Round robin</b> – toți joacă împotriva tuturor, într-o singură grupă ordonată după rating.</p>
            <p><b>Grupe + finale</b> – în două etape:</p>
            <ol>
              <li><b>Etapa 1 – grupe.</b> Jucătorii sunt repartizați „în șerpuială” după rating (A, B, C, C, B, A, …), ca grupele să fie echilibrate. Fiecare grupă joacă round robin.</li>
              <li><b>Etapa 2 – finale.</b> După grupe, organizatorul alege câți jucători din fiecare grupă trec în <b>Finala 1</b>; ceilalți joacă <b>Finala 2</b>.
                  Ambele finale se joacă round robin, dar jucătorii care s-au întâlnit deja în grupă nu mai joacă: rezultatul din etapa 1 se preia.
                  După începerea etapei 2, rezultatele din grupe nu se mai pot schimba.</li>
            </ol>

            <h3>4. Punctele și clasamentul în grupă</h3>
            <table>
              <tr><th>Rezultat</th><th>Puncte</th></tr>
              <tr><td>Victorie</td><td>2</td></tr>
              <tr><td>Înfrângere (meci jucat)</td><td>1</td></tr>
              <tr><td>Înfrângere tehnică (neprezentare)</td><td>0</td></tr>
            </table>
            <p>O victorie tehnică se socotește la seturi ca o victorie la scor alb (3:0 la best of 5).
            În tabelul grupei fiecare întâlnire apare ca fracție: sus punctele, jos scorul la seturi.</p>
            <p>Locul se stabilește după puncte. La egalitate de puncte decide, în ordine:</p>
            <ol>
              <li>punctele din meciurile directe dintre jucătorii aflați la egalitate;</li>
              <li>raportul seturilor câștigate / pierdute în aceste meciuri directe;</li>
              <li>raportul seturilor câștigate / pierdute în toate meciurile din grupă;</li>
              <li>poziția în grupă (ratingul de la începutul turneului).</li>
            </ol>

            <h3>5. Turnee comerciale și premii</h3>
            <p>Suma acumulată este taxa de participare × numărul de participanți. Ea se împarte astfel:</p>
            <table>
              <tr><th>Premiați</th><th>Împărțire</th></tr>
              <tr><td>1</td><td>100%</td></tr>
              <tr><td>2</td><td>60% / 40%</td></tr>
              <tr><td>3</td><td>50% / 30% / 20%</td></tr>
            </table>
            <p>Fiecare premiu se rotunjește la cel mai apropiat multiplu de 50 de lei (de exemplu 420 → 400, 280 → 300, 225 → 250),
            deci totalul premiilor poate diferi puțin de suma acumulată.</p>
            <p>La <b>Grupe + finale</b>, câștigătorul Finalei 2 primește cât taxa de participare, iar restul sumei se împarte,
            după tabelul de mai sus, între premiații Finalei 1. Un astfel de turneu se poate încheia manual doar după începerea etapei 2.</p>

            <h3>6. Ratingul (Elo)</h3>
            <p>Fiecare jucător are un rating, care pornește de la ratingul inițial (implicit 1000) și se modifică după fiecare turneu încheiat.
            Pentru fiecare meci jucat, se calculează întâi probabilitatea de victorie, apoi schimbarea:</p>
            <div class="formula">E<sub>A</sub> = 1 / (1 + 10<sup>(R<sub>B</sub> − R<sub>A</sub>) / 400</sup>)
            ΔR<sub>A</sub> = K × (S<sub>A</sub> − E<sub>A</sub>)</div>
            <ul>
              <li><b>R<sub>A</sub>, R<sub>B</sub></b> – ratingurile celor doi jucători la <b>începutul turneului</b> (toate meciurile unui turneu folosesc aceleași ratinguri, deci ordinea meciurilor nu contează);</li>
              <li><b>S<sub>A</sub></b> – 1 pentru victorie, 0 pentru înfrângere (scorul la seturi nu contează);</li>
              <li><b>K</b> – 40 cât timp jucătorul are mai puțin de 30 de meciuri cu rating, apoi 20 (stabilit la începutul fiecărui turneu);</li>
              <li>o diferență de rating mai mare de 400 de puncte se socotește ca 400;</li>
              <li>schimbările din toate meciurile turneului se adună, iar totalul se rotunjește la număr întreg;</li>
              <li>victoriile tehnice nu modifică ratingul (dar contează la clasamentul grupei);</li>
              <li>ratingul nu poate scădea sub 0.</li>
            </ul>
            <p><b>Exemplu.</b> Ana (1500) o învinge pe Elena (1400), ambele cu K = 40:</p>
            <div class="formula">E<sub>Ana</sub> = 1 / (1 + 10<sup>−100/400</sup>) = 0,64     ΔR<sub>Ana</sub> = 40 × (1 − 0,64) = +14
            E<sub>Elena</sub> = 0,36                              ΔR<sub>Elena</sub> = 40 × (0 − 0,36) = −14</div>
            <p>Dacă ar fi câștigat Elena, schimbarea ar fi fost de ±26: o victorie neașteptată aduce mai multe puncte.</p>
            <p class="note">Ratingul se recalculează în ordine cronologică, de la ratingul inițial, din toate turneele încheiate.
            De aceea o corectură într-un turneu mai vechi se reflectă și în turneele de după el.</p>

            <h3>7. Meciuri, jucători și profil</h3>
            <ul>
              <li><b>Meciuri</b> (pagina principală): ultimele meciuri cu rezultat, cu căutare după jucător sau turneu. Lângă nume apare schimbarea de rating, după încheierea turneului.</li>
              <li><b>Pagina jucătorului</b> (apăsați pe un nume): locul în clasament, statistici (meciuri, victorii, procent, seturi, turnee, cel mai bun rating), echipamentul, evoluția ratingului și toate meciurile lui.</li>
              <li><b>Profilul meu</b>: datele personale, mâna de joc, echipamentul, parola și poza. Poza se decupează pătrat pentru liste și se poate vedea mărită apăsând pe ea.</li>
            </ul>

            <h3>8. Aplicația pe telefon</h3>
            <p>TTM se poate folosi ca o aplicație obișnuită. Pe iPhone: în Safari, butonul <b>Share</b> → <b>Add to Home Screen</b>.
            Pe Android: în Chrome, meniul <b>⋮</b> → <b>Adăugați pe ecranul de pornire</b>.</p>
            </div>
            """;
}
