package de.damcraft.serverseeker.gui;

/** A yes/no/any filter used throughout the search screens. */
public enum TriState {
    Any,
    Yes,
    No;

    public Boolean toBoolOrNull() {
        return switch (this) {
            case Any -> null;
            case Yes -> true;
            case No -> false;
        };
    }
}
