package android.util;

public abstract class Singleton<T> {
    private T mInstance;
    protected abstract T create();
    public final T get() {
        return null;
    }
}
