package com.anistream.app;

import android.text.Editable;
import android.text.TextWatcher;

/** Subclass satu metode agar listener teks tidak berisik di pemanggilan. */
public abstract class SimpleTextWatcher implements TextWatcher {

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        onTextChanged(s);
    }

    @Override
    public void afterTextChanged(Editable s) {}

    public abstract void onTextChanged(CharSequence s);
}
