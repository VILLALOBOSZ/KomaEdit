package com.komainu.moxia.client.utility;

import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.feedback.Words;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Los textos del cliente con el idioma del juego, para lo que arma Java puro (la regla, las notas). */
public final class ClientWords implements Words {
    public static final ClientWords INSTANCE = new ClientWords();

    private ClientWords() {
    }

    @Override
    public String tr(String key, Object... arguments) {
        return I18n.translate(key, arguments);
    }

    /** El prefijo de los mensajes de KomaEdit ({@code KE ·}), como los del servidor. */
    public static MutableText prefix() {
        return Text.literal(Feedback.PREFIX).formatted(Formatting.GOLD)
                .append(Text.literal(" · ").formatted(Formatting.DARK_GRAY));
    }
}
