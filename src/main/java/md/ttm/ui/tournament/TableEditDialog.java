package md.ttm.ui.tournament;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import md.ttm.i18n.Tr;
import md.ttm.model.tournament.TournamentMatch;
import md.ttm.model.tournament.TournamentParticipant;
import md.ttm.ui.components.Notifications;
import md.ttm.ui.components.PlayerAvatar;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * „Editează grupa” (sau tabelul Round Robin) după începerea turneului: lista jucătorilor, cu butonul de scoatere,
 * și butonul care deschide adăugarea de jucători noi.
 */
class TableEditDialog extends Dialog {

    TableEditDialog(String title, String explanation, List<TournamentParticipant> members, List<TournamentMatch> matches,
                    Map<Long, Long> photoVersions, Runnable onAdd, Consumer<TournamentParticipant> onRemove) {
        setHeaderTitle(title);
        setWidth("min(96vw, 30rem)");
        Button close = new Button(VaadinIcon.CLOSE.create(), e -> close());
        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        close.setAriaLabel(Tr.t("Închide"));
        getHeader().add(close);

        Paragraph info = new Paragraph(explanation);
        info.getStyle().set("margin", "0").set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");
        Div list = new Div();
        list.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "2px");
        for (TournamentParticipant p : members) {
            long played = matches.stream()
                    .filter(m -> m.isPlayed() && (m.getParticipantA().getId().equals(p.getId())
                            || m.getParticipantB().getId().equals(p.getId())))
                    .count();
            Span name = new Span(p.getPlayer().getDisplayName());
            name.getStyle().set("font-weight", "500");
            Span sub = new Span(Tr.t("Rating {0} · meciuri jucate: {1}", p.getPlayer().getRating(), played));
            sub.getStyle().set("font-size", "var(--lumo-font-size-s)").set("color", "var(--lumo-secondary-text-color)");
            Div text = new Div(name, sub);
            text.getStyle().set("display", "flex").set("flex-direction", "column").set("flex", "1").set("min-width", "0");
            Button remove = new Button(VaadinIcon.CLOSE_SMALL.create(), e -> confirmRemove(p, played, onRemove));
            remove.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
            remove.setAriaLabel(Tr.t("Scoate {0}", p.getPlayer().getDisplayName()));
            remove.setTooltipText(Tr.t("Scoate din grupă"));
            Div row = new Div(PlayerAvatar.of(p.getPlayer(), photoVersions.get(p.getPlayer().getId()), "2rem"), text, remove);
            row.addClassName("ttm-table-member");
            row.getStyle().set("display", "flex").set("align-items", "center").set("gap", "var(--lumo-space-m)")
                    .set("padding", "var(--lumo-space-xs) 0").set("border-bottom", "1px solid var(--lumo-contrast-10pct)");
            list.add(row);
        }
        add(info, list);
        list.getStyle().set("margin-top", "var(--lumo-space-s)");

        Button add = new Button(Tr.t("Adaugă jucători"), VaadinIcon.PLUS.create(), e -> {
            close();
            onAdd.run();
        });
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        getFooter().add(add);
    }

    private void confirmRemove(TournamentParticipant p, long played, Consumer<TournamentParticipant> onRemove) {
        ConfirmDialog confirm = new ConfirmDialog();
        confirm.setHeader(Tr.t("Scoateți {0}?", p.getPlayer().getDisplayName()));
        confirm.setText(played == 0
                ? Tr.t("Meciurile lui din acest tabel se șterg.")
                : Tr.t("Meciurile lui din acest tabel se șterg, inclusiv {0} cu rezultat. Clasamentul se recalculează.", played));
        confirm.setCancelable(true);
        confirm.setCancelText(Tr.t("Anulează"));
        confirm.setConfirmText(Tr.t("Scoate"));
        confirm.setConfirmButtonTheme("error primary");
        confirm.addConfirmListener(e -> {
            try {
                onRemove.accept(p);
                close();
            } catch (RuntimeException ex) {
                Notifications.error(Notifications.saveError(ex));
            }
        });
        confirm.open();
    }
}
