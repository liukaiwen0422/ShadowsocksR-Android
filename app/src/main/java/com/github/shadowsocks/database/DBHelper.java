package com.github.shadowsocks.database;


import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import com.github.shadowsocks.ShadowsocksApplication;
import com.github.shadowsocks.utils.VayLog;
import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DBHelper extends OrmLiteSqliteOpenHelper {

    public static final String PROFILE = "profile.db";
    private static final String TAG = DBHelper.class.getSimpleName();
    private static final int VERSION = 25;
    Dao<Profile, Integer> profileDao;
    Dao<SSRSub, Integer> ssrsubDao;
    private List<ApplicationInfo> apps;
    private Context context;
    public DBHelper(Context context) {
        super(context, DBHelper.PROFILE, null, VERSION);
        this.context = context;
        try {
            profileDao = getDao(Profile.class);
        } catch (SQLException e) {
            VayLog.e(TAG, "", e);
        }
        try {
            ssrsubDao = getDao(SSRSub.class);
        } catch (SQLException e) {
            VayLog.e(TAG, "", e);
        }
    }

    /**
     * is all digits
     */
    private boolean isAllDigits(String x) {
        if (!TextUtils.isEmpty(x)) {
            for (char ch : x.toCharArray()) {
                boolean digit = Character.isDigit(ch);
                if (!digit) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * update proxied apps
     */
    private synchronized String updateProxiedApps(Context context, String old) {
        if (apps == null) {
            apps = context.getPackageManager().getInstalledApplications(0);
        }

        List<Integer> uidSet = new ArrayList<>();
        String[] split = old.split("|");
        for (String item : split) {
            if (isAllDigits(item)) {
                // add to uid list
                uidSet.add(Integer.parseInt(item));
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < apps.size(); i++) {
            ApplicationInfo ai = apps.get(i);
            if (uidSet.contains(ai.uid)) {
                if (i > 0) {
                    // adding separator
                    sb.append("\n");
                }
                sb.append(ai.packageName);
            }
        }
        return sb.toString();
    }

    @Override
    public void onCreate(SQLiteDatabase database, ConnectionSource connectionSource) {
        try {
            TableUtils.createTable(connectionSource, Profile.class);
            TableUtils.createTable(connectionSource, SSRSub.class);
        } catch (SQLException e) {
            VayLog.e(TAG, "onCreate", e);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, ConnectionSource connectionSource, int oldVersion, int newVersion) {
        if (oldVersion != newVersion) {
            try {
                if (oldVersion < 7) {
                    profileDao.executeRawNoArgs("DROP TABLE IF EXISTS 'profile';");
                    onCreate(database, connectionSource);
                    return;
                }
                if (oldVersion < 8) {
                    profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN udpdns SMALLINT;");
                }
                if (oldVersion < 9) {
                    profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN route VARCHAR DEFAULT 'all';");
                } else if (oldVersion < 19) {
                    profileDao.executeRawNoArgs("UPDATE `profile` SET route = 'all' WHERE route IS NULL;");
                }
                if (oldVersion < 10) {
                    profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN auth SMALLINT;");
                }
                if (oldVersion < 11) {
                    profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN ipv6 SMALLINT;");
                }
                if (oldVersion < 12) {
                    profileDao.executeRawNoArgs("BEGIN TRANSACTION;");
                    profileDao.executeRawNoArgs("ALTER TABLE `profile` RENAME TO `tmp`;");
                    TableUtils.createTable(connectionSource, Profile.class);
                    profileDao.executeRawNoArgs(
                            "INSERT INTO `profile`(id, name, host, localPort, remotePort, password, method, route, proxyApps, bypass," +
                                    " udpdns, auth, ipv6, individual) " +
                                    "SELECT id, name, host, localPort, remotePort, password, method, route, 1 - global, bypass, udpdns, auth," +
                                    " ipv6, individual FROM `tmp`;");
                    profileDao.executeRawNoArgs("DROP TABLE `tmp`;");
                    profileDao.executeRawNoArgs("COMMIT;");
                } else if (oldVersion < 13) {
                    profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN tx LONG;");
                    profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN rx LONG;");
                    profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN date VARCHAR;");
                }

                if (oldVersion < 15) {
                    if (oldVersion >= 12) {
                        profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN userOrder LONG;");
                    }
                    int i = 0;
                    for (Profile profile : profileDao.queryForAll()) {
                        if (oldVersion < 14) {
                            profile.individual = updateProxiedApps(context, profile.individual);
                        }
                        profile.userOrder = i;
                        profileDao.update(profile);
                        i += 1;
                    }
                }


                if (oldVersion < 16) {
                    profileDao.executeRawNoArgs("UPDATE `profile` SET route = 'bypass-lan-china' WHERE route = 'bypass-china'");
                }

                if (oldVersion < 19) {
                    profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN dns VARCHAR DEFAULT '8.8.8.8:53';");
                }

                if (oldVersion < 20) {
                    profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN china_dns VARCHAR DEFAULT '114.114.114.114:53,223.5.5.5:53';");
                }

                if (oldVersion < 21) {
                    profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN protocol_param VARCHAR DEFAULT '';");
                }

                if (oldVersion < 22) {
                    profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN elapsed LONG DEFAULT 0;");
                }

                if (oldVersion < 23) {
                    profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN tcpdelay LONG DEFAULT 0;");
                }

                if (oldVersion < 24) {
                    profileDao.executeRawNoArgs("ALTER TABLE `profile` ADD COLUMN url_group VARCHAR DEFAULT '';");
                }

                if (oldVersion < 25) {
                    TableUtils.createTable(connectionSource, SSRSub.class);
                }

            } catch (Exception e) {
                VayLog.e(TAG, "onUpgrade", e);
                ShadowsocksApplication.app.track(e);

                try {
                    profileDao.executeRawNoArgs("DROP TABLE IF EXISTS 'profile';");
                } catch (SQLException e1) {
                    VayLog.e(TAG, "onUpgrade", e);
                }
                onCreate(database, connectionSource);
            }
        }
    }
}
