package io.locative.app.persistent;

import android.support.annotation.NonNull;

import java.io.Serializable;

public abstract class EditableItem implements Serializable {

    @NonNull
    public abstract String getDatabaseId();
}
