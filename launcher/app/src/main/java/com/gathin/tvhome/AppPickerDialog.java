package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * What a remote button does, for the Key bindings page: Nothing, the Sources menu, Close all apps, then every
 * installed app with a TV or a phone entry, by name. The pick is a target as app_keys has them ("" for Nothing).
 */
final class AppPickerDialog {

    private AppPickerDialog() { }

    static void show(Activity activity, CharSequence title, String current, Consumer<String> picked) {
        List<String[]> choices = new ArrayList<>();
        choices.add(new String[]{activity.getString(R.string.target_nothing), ""});
        choices.add(new String[]{activity.getString(R.string.target_inputs), RemoteKeys.INPUTS});
        choices.add(new String[]{activity.getString(R.string.target_close_apps), KeyBindings.CLOSE_APPS});
        choices.addAll(apps(activity));
        String[] labels = new String[choices.size()];
        int checked = -1;
        for (int i = 0; i < choices.size(); i++) {
            labels[i] = choices.get(i)[0];
            if (choices.get(i)[1].equals(current == null ? "" : current)) checked = i;
        }
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    picked.accept(choices.get(which)[1]);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** {label, package} for each app with a launcher entry, the TV one or the phone one, but not this launcher. */
    private static List<String[]> apps(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        List<String[]> apps = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo r : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                String pkg = r.activityInfo.packageName;
                if (pkg.equals(ctx.getPackageName()) || seen.contains(pkg)) continue;
                seen.add(pkg);
                apps.add(new String[]{r.loadLabel(pm).toString(), pkg});
            }
        }
        Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        return apps;
    }

    /** A target as the page shows it: Nothing, the Sources menu, Close all apps, an app's name, else the target itself. */
    static String label(Context ctx, String target) {
        if (target == null || target.isEmpty()) return ctx.getString(R.string.target_nothing);
        if (RemoteKeys.INPUTS.equals(target)) return ctx.getString(R.string.target_inputs);
        if (KeyBindings.CLOSE_APPS.equals(target)) return ctx.getString(R.string.target_close_apps);
        try {
            PackageManager pm = ctx.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(target, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return target;
        }
    }
}
