/*
 * Copyright (C) 2026 The DiamaneOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.contacts.quickcontact;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.widget.Toast;

import com.android.contacts.CallUtil;
import com.android.contacts.R;
import com.android.contacts.activities.TransactionSafeActivity;
import com.android.contacts.util.ImplicitIntentsUtil;
import com.android.contacts.util.PermissionsUtil;
import com.android.contacts.util.PhoneNumberHelper;
import com.android.contacts.widget.SelectPhoneAccountDialogFragment;

import java.util.Collections;
import java.util.List;

/** Choose SIM for this call, in a phone number's menu in QuickContactActivity. */
final class CallWithSim {
    private static final String TAG = "QuickContact";
    private static final String TAG_SELECT_PHONE_ACCOUNT = "select_phone_account";

    private CallWithSim() {}

    /** With two or more SIMs, offers to choose the SIM for a call to this number. */
    static void addMenuItem(Context context, ContextMenu menu, int itemId, String number) {
        if (number != null && !getCallAccountChoices(context, number).isEmpty()) {
            menu.add(
                    ContextMenu.NONE,
                    itemId,
                    ContextMenu.NONE,
                    context.getString(R.string.call_with_sim));
        }
    }

    /**
     * Returns the call-capable phone accounts the user can choose from to call {@code number}.
     * Empty when there is no choice to offer: fewer than two accounts, a SIP address, an emergency
     * number (left to the platform to route), or no permission to read phone state.
     */
    static List<PhoneAccountHandle> getCallAccountChoices(Context context, String number) {
        if (TextUtils.isEmpty(number) || PhoneNumberHelper.isUriNumber(number)
                || PhoneNumberUtils.isEmergencyNumber(number)
                || !PermissionsUtil.hasPermission(
                        context, android.Manifest.permission.READ_PHONE_STATE)) {
            return Collections.emptyList();
        }
        final TelecomManager telecomManager = context.getSystemService(TelecomManager.class);
        if (telecomManager == null) {
            return Collections.emptyList();
        }
        try {
            final List<PhoneAccountHandle> accountHandles =
                    telecomManager.getCallCapablePhoneAccounts();
            return accountHandles != null && accountHandles.size() > 1
                    ? accountHandles : Collections.emptyList();
        } catch (SecurityException e) {
            Log.w(CallUtil.TAG, "Cannot read call capable phone accounts", e);
            return Collections.emptyList();
        }
    }

    /**
     * Return an Intent for calling {@code number} through {@code phoneAccountHandle}. Not for
     * emergency numbers. See {@link #getCallAccountChoices}.
     */
    static Intent getCallIntent(String number, PhoneAccountHandle phoneAccountHandle) {
        final Intent intent = CallUtil.getCallIntent(CallUtil.getCallUri(number));
        intent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle);
        return intent;
    }

    /** Lets the user choose the SIM, then calls {@code number} through it. */
    static void showDialog(TransactionSafeActivity activity, String number) {
        final List<PhoneAccountHandle> choices = getCallAccountChoices(activity, number);
        if (choices.isEmpty() || !activity.isSafeToCommitTransactions()) {
            // SIMs changed since the menu opened, or the activity is going away.
            return;
        }
        SelectPhoneAccountDialogFragment.newInstance(
                        R.string.call_with_sim,
                        /* canSetDefault= */ false,
                        choices,
                        new CallWithSimListener(activity.getApplicationContext(), number))
                .show(activity.getFragmentManager(), TAG_SELECT_PHONE_ACCOUNT);
    }

    /** Calls the number through the chosen SIM. Holds no reference to the activity. */
    private static final class CallWithSimListener
            extends SelectPhoneAccountDialogFragment.SelectPhoneAccountListener {
        private final Context mAppContext;
        private final String mNumber;

        CallWithSimListener(Context appContext, String number) {
            mAppContext = appContext;
            mNumber = number;
        }

        @Override
        public void onPhoneAccountSelected(
                PhoneAccountHandle selectedAccountHandle, boolean setDefault) {
            final Intent intent = getCallIntent(mNumber, selectedAccountHandle);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                ImplicitIntentsUtil.startActivityInAppIfPossible(mAppContext, intent);
            } catch (SecurityException | ActivityNotFoundException ex) {
                Toast.makeText(mAppContext, R.string.missing_app, Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Cannot place call with chosen SIM", ex);
            }
        }
    }
}
