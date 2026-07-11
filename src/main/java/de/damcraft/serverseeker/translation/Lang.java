package de.damcraft.serverseeker.translation;

public enum Lang {
    ENGLISH_US("EN-US", "English (US)"),
    ENGLISH_GB("EN-GB", "English (UK)"),
    CHINESE("ZH", "Chinese"),
    VIETNAMESE("VI", "Vietnamese"),
    PORTUGUESE_BR("PT-BR", "Portuguese (Brazil)"),
    PORTUGUESE_PT("PT-PT", "Portuguese (Portugal)"),
    SPANISH("ES", "Spanish"),
    FRENCH("FR", "French"),
    GERMAN("DE", "German"),
    ITALIAN("IT", "Italian"),
    DUTCH("NL", "Dutch"),
    POLISH("PL", "Polish"),
    RUSSIAN("RU", "Russian"),
    UKRAINIAN("UK", "Ukrainian"),
    JAPANESE("JA", "Japanese"),
    KOREAN("KO", "Korean"),
    TURKISH("TR", "Turkish"),
    ARABIC("AR", "Arabic"),
    INDONESIAN("ID", "Indonesian"),
    CZECH("CS", "Czech"),
    DANISH("DA", "Danish"),
    FINNISH("FI", "Finnish"),
    GREEK("EL", "Greek"),
    HUNGARIAN("HU", "Hungarian"),
    ROMANIAN("RO", "Romanian"),
    SWEDISH("SV", "Swedish"),
    NORWEGIAN("NB", "Norwegian"),
    BULGARIAN("BG", "Bulgarian");

    private final String code;
    private final String display;

    Lang(String code, String display) {
        this.code = code;
        this.display = display;
    }

    public String code() {
        return code;
    }

    public String sourceCode() {
        int dash = code.indexOf('-');
        return dash < 0 ? code : code.substring(0, dash);
    }

    @Override
    public String toString() {
        return display;
    }
}
