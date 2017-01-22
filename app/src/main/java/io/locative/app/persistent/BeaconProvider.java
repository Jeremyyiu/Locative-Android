package io.locative.app.persistent;

import de.triplet.simpleprovider.AbstractProvider;
import de.triplet.simpleprovider.Column;
import de.triplet.simpleprovider.Table;
import io.locative.app.R;

public class BeaconProvider extends AbstractProvider {

    private static int SCHEMA_VERSION = 1;

    public enum BeaconType {
        IBEACON(0),
        EDDYSTONE(1);

        BeaconType(int value) {
            mValue = value;
        }

        private final int mValue;

        public int getValue() {
            return mValue;
        }
    }

    @Override
    protected String getAuthority() {
        return getContext().getString(R.string.authority);
    }

    @Override
    public int getSchemaVersion() {
        return SCHEMA_VERSION;
    }

    @Table
    public class Beacon {
        @Column(value = Column.FieldType.INTEGER, primaryKey = true)
        public static final String KEY_ID = "_id";

        @Column(Column.FieldType.INTEGER)
        public static final String KEY_BEACON_TYPE = "beacon_type";

        @Column(value = Column.FieldType.TEXT)
        public static final String KEY_CUSTOMID = "custom_id";

        @Column(Column.FieldType.INTEGER)
        public static final String KEY_ENTER_METHOD = "enter_method";

        @Column(Column.FieldType.TEXT)
        public static final String KEY_ENTER_URL = "enter_url";

        @Column(Column.FieldType.INTEGER)
        public static final String KEY_EXIT_METHOD = "exit_method";

        @Column(Column.FieldType.TEXT)
        public static final String KEY_EXIT_URL = "exit_url";

        @Column(Column.FieldType.INTEGER)
        public static final String KEY_HTTP_AUTH = "http_auth";

        @Column(Column.FieldType.TEXT)
        public static final String KEY_HTTP_USERNAME = "http_username";

        @Column(Column.FieldType.TEXT)
        public static final String KEY_HTTP_PASSWORD = "http_password";

        @Column(Column.FieldType.INTEGER)
        public static final String KEY_TRIGGER = "triggers";

        @Column(Column.FieldType.TEXT)
        public static final String KEY_UUID = "uuid";

        @Column(Column.FieldType.INTEGER)
        public static final String KEY_MAJOR = "major";

        @Column(Column.FieldType.INTEGER)
        public static final String KEY_MINOR = "minor";
    }
}
