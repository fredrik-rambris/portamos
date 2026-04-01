package dev.rambris.amos.bank;

public interface AmosBank {
    enum Type {
        MUSIC("Music   "),
        TRACKER("Tracker "),
        AMAL("Amal    "),
        MENU("Menu    "),
        DATAS("Datas   "),
        DATA("Data    "),
        WORK("Work    "),
        ASM("Asm     "),
        CODE("Code    "),
        PACPIC("Pac.Pic."),
        RESOURCE("Resource"),
        SAMPLES("Samples ");

        private final String identifier;

        Type(String identifier) {
            this.identifier = identifier;
        }

        public String identifier() {
            return identifier;
        }
    }

    Type type();
    short bankNumber();
    boolean chipRam();
}
