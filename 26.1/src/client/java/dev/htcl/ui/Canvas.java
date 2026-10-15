package dev.htcl.ui;

public interface Canvas {
    void fill(float x, float y, float w, float h, float r, int top, int bottom);

    void ring(float x, float y, float w, float h, float r, float t, int top, int bottom);

    void shadow(float x, float y, float w, float h, float r, float blur, int argb);

    void hue(float x, float y, float w, float h, float r, int argb);

    void sv(float x, float y, float w, float h, float r, int argb);

    void text(String s, float x, float y, int argb);

    int width(String s);

    default void item(String id, float x, float y, float size) {
    }

    default boolean player(float x, float y, float w, float h) {
        return false;
    }

    default void hurt() {
    }

    default void fill(float x, float y, float w, float h, float r, int argb) {
        fill(x, y, w, h, r, argb, argb);
    }

    default void ring(float x, float y, float w, float h, float r, float t, int argb) {
        ring(x, y, w, h, r, t, argb, argb);
    }
}
