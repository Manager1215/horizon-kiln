import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;

/**
 * Mods' chat lines through Kiln's {@code ChatComponent} stand-in, as they leave for the game: the reply lines Kiln's
 * Rust side reads, for kiln/tests/chat_style.rs. It runs as a source file with kiln-runtime.jar on the class path:
 * {@code java -cp kiln-runtime.jar ChatStyle.java}. {@code Host.main} isn't running, so its output is set here.
 */
public class ChatStyle {
    public static void main(String[] args) throws Exception {
        var out = Class.forName("kiln.Host").getDeclaredField("out");
        out.setAccessible(true);
        out.set(null, new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        ChatComponent chat = new ChatComponent();
        // Calc's division-by-zero error (#82).
        chat.addMessage(Component.literal("Division by zero")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD, ChatFormatting.ITALIC));
        chat.addClientSystemMessage(Component.literal("sky").withColor(0x55AAFF));
        chat.addMessage(Component.literal("§cred by its code"));
        // A mod's own translation (Quick Resource Pack's "No resource pack configured"): the game has no text for its
        // key, so it goes as the mod's text; with a style, as styled text. The game's own key stays a translation.
        chat.addMessage(Component.translatable("key.kiln_test_keys.press"));
        chat.addMessage(Component.translatable("key.kiln_test_keys.press").withStyle(ChatFormatting.RED));
        chat.addMessage(Component.translatable("gui.done"));
        // #217 (D-185): a format set off goes as false (an item's custom name upright); one set back to unset, not at all.
        chat.addMessage(Component.literal("upright").withStyle(s -> s.withItalic(false)));
        chat.addMessage(Component.literal("unset").withStyle(s -> s.withItalic(true).withItalic(null)));
        // Read back as the game saves a custom name, and over a parent's italic: the child's false wins.
        chat.addMessage(net.minecraft.network.chat.ComponentSerialization.read(
                com.google.gson.JsonParser.parseString("{\"text\":\"saved\",\"italic\":false,\"bold\":true}"))
                .getOrThrow());
        System.out.println("kiln-test italic " + net.minecraft.network.chat.Style.EMPTY.withItalic(true)
                .applyTo(net.minecraft.network.chat.Style.EMPTY.withItalic(false)).isItalic());
    }
}
