package net.minecraft.client.input;

/** Kiln's stand-in for {@code CharacterEvent}: a character typed, by its code point. */
public record CharacterEvent(int codepoint) {
    public String codepointAsString() {
        return Character.toString(codepoint);
    }

    /** Whether chat and text boxes take it: not a control character, nor the section sign. */
    public boolean isAllowedChatCharacter() {
        return codepoint >= 32 && codepoint != 127 && codepoint != 167;
    }
}
