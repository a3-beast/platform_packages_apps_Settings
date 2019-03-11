/* Copyright Statement:
 *
 * This software/firmware and related documentation ("MediaTek Software") are
 * protected under relevant copyright laws. The information contained herein
 * is confidential and proprietary to MediaTek Inc. and/or its licensors.
 * Without the prior written permission of MediaTek inc. and/or its licensors,
 * any reproduction, modification, use or disclosure of MediaTek Software,
 * and information contained herein, in whole or in part, shall be strictly prohibited.
 */
/* MediaTek Inc. (C) 2017. All rights reserved.
 *
 * BY OPENING THIS FILE, RECEIVER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
 * THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
 * RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO RECEIVER ON
 * AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
 * NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
 * SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
 * SUPPLIED WITH THE MEDIATEK SOFTWARE, AND RECEIVER AGREES TO LOOK ONLY TO SUCH
 * THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. RECEIVER EXPRESSLY ACKNOWLEDGES
 * THAT IT IS RECEIVER'S SOLE RESPONSIBILITY TO OBTAIN FROM ANY THIRD PARTY ALL PROPER LICENSES
 * CONTAINED IN MEDIATEK SOFTWARE. MEDIATEK SHALL ALSO NOT BE RESPONSIBLE FOR ANY MEDIATEK
 * SOFTWARE RELEASES MADE TO RECEIVER'S SPECIFICATION OR TO CONFORM TO A PARTICULAR
 * STANDARD OR OPEN FORUM. RECEIVER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND
 * CUMULATIVE LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
 * AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
 * OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY RECEIVER TO
 * MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
 *
 * The following software/firmware and/or related documentation ("MediaTek Software")
 * have been modified by MediaTek Inc. All revisions are subject to any receiver's
 * applicable license agreements with MediaTek Inc.
 */

package com.mediatek.settings.sim;

import android.content.Context;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import android.util.Log;

import com.android.internal.telephony.ITelephony;

import com.mediatek.internal.telephony.IMtkTelephonyEx;
import com.mediatek.internal.telephony.MtkIccCardConstants;
import com.mediatek.internal.telephony.RadioCapabilitySwitchUtil;
import com.mediatek.telephony.MtkTelephonyManagerEx;

public class TelephonyUtils {
    private static boolean DBG =
            SystemProperties.get("ro.build.type").equals("eng") ? true : false;
    private static final String TAG = "TelephonyUtils";

    /// M: Define the SIM lock policy/valid/capability. @{
    public static final int SIM_LOCK_POLICY_UNKNOWN =
            MtkIccCardConstants.SML_SLOT_LOCK_POLICY_UNKNOWN;
    public static final int SIM_LOCK_POLICY_NONE =
            MtkIccCardConstants.SML_SLOT_LOCK_POLICY_NONE;
    public static final int SIM_LOCK_POLICY_SLOT1_ONLY =
            MtkIccCardConstants.SML_SLOT_LOCK_POLICY_ONLY_SLOT1;
    public static final int SIM_LOCK_POLICY_SLOT2_ONLY =
            MtkIccCardConstants.SML_SLOT_LOCK_POLICY_ONLY_SLOT2;
    public static final int SIM_LOCK_POLICY_SLOT_ALL =
            MtkIccCardConstants.SML_SLOT_LOCK_POLICY_ALL_SLOTS_INDIVIDUAL;
    public static final int SIM_LOCK_POLICY_SLOT1_LINKED =
            MtkIccCardConstants.SML_SLOT_LOCK_POLICY_LK_SLOT1;
    public static final int SIM_LOCK_POLICY_SLOT2_LINKED =
            MtkIccCardConstants.SML_SLOT_LOCK_POLICY_LK_SLOT2;
    public static final int SIM_LOCK_POLICY_SLOT_ALL_LINKED =
            MtkIccCardConstants.SML_SLOT_LOCK_POLICY_LK_SLOTA;
    public static final int SIM_LOCK_POLICY_SLOT_ALL_LINKED_WITH_CS =
            MtkIccCardConstants.SML_SLOT_LOCK_POLICY_LK_SLOTA_RESTRICT_INVALID_CS;
    public static final int SIM_LOCK_POLICY_LEGACY =
            MtkIccCardConstants.SML_SLOT_LOCK_POLICY_LEGACY;

    public static final int SIM_LOCK_SIM_VALID_UNKNOWN =
            MtkIccCardConstants.SML_SLOT_LOCK_POLICY_VALID_CARD_UNKNOWN;
    public static final int SIM_LOCK_SIM_VALID_YES =
            MtkIccCardConstants.SML_SLOT_LOCK_POLICY_VALID_CARD_YES;
    public static final int SIM_LOCK_SIM_VALID_NO =
            MtkIccCardConstants.SML_SLOT_LOCK_POLICY_VALID_CARD_NO;
    public static final int SIM_LOCK_SIM_VALID_ABSENT =
            MtkIccCardConstants.SML_SLOT_LOCK_POLICY_VALID_CARD_ABSENT;

    public static final int SIM_LOCK_SIM_CAPABILITY_UNKNOWN =
            MtkIccCardConstants.SML_SLOT_LOCK_POLICY_SERVICE_CAPABILITY_UNKNOWN;
    public static final int SIM_LOCK_SIM_CAPABILITY_FULL =
            MtkIccCardConstants.SML_SLOT_LOCK_POLICY_SERVICE_CAPABILITY_FULL;
    public static final int SIM_LOCK_SIM_CAPABILITY_CS_ONLY =
            MtkIccCardConstants.SML_SLOT_LOCK_POLICY_SERVICE_CAPABILITY_CS_ONLY;
    public static final int SIM_LOCK_SIM_CAPABILITY_PS_ONLY =
            MtkIccCardConstants.SML_SLOT_LOCK_POLICY_SERVICE_CAPABILITY_PS_ONLY;
    public static final int SIM_LOCK_SIM_CAPABILITY_ECC_ONLY =
            MtkIccCardConstants.SML_SLOT_LOCK_POLICY_SERVICE_CAPABILITY_ECC_ONLY;
    public static final int SIM_LOCK_SIM_CAPABILITY_NO_SERVICE =
            MtkIccCardConstants.SML_SLOT_LOCK_POLICY_SERVICE_CAPABILITY_NO_SERVICE;

    public static final int SIM_LOCK_CASE_UNKNOWN = -1;
    public static final int SIM_LOCK_CASE_NONE = 0;
    public static final int SIM_LOCK_CASE_ALL_EMPTY = 1; // all empty
    public static final int SIM_LOCK_CASE_ALL_UNKNOWN = 2; // all unknown
    public static final int SIM_LOCK_CASE_ALL_VALID = 3; // all valid
    public static final int SIM_LOCK_CASE_ALL_INVALID = 4; // all invalid
    public static final int SIM_LOCK_CASE_INVALID_1_VALID = 5; // invalid + single valid
    public static final int SIM_LOCK_CASE_INVALID_N_VALID = 6; // invalid + multiple valid
    public static final int SIM_LOCK_CASE_UNKNOWN_INVALID = 7; // unknown + invalid
    public static final int SIM_LOCK_CASE_UNKNOWN_1_VALID = 8; // unknown + single valid
    public static final int SIM_LOCK_CASE_UNKNOWN_N_VALID = 9; // unknown + multiple valid
    // unknown + invalid + single valid
    public static final int SIM_LOCK_CASE_UNKNOWN_INVALID_1_VALID = 10;
    // unknown + invalid + multiple valid
    public static final int SIM_LOCK_CASE_UNKNOWN_INVALID_N_VALID = 11;
    /// @}

    /**
     * Get whether airplane mode is in on.
     * @param context Context.
     * @return True for on.
     */
    public static boolean isAirplaneModeOn(Context context) {
        return Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }

    /**
     * Calling API to get subId is in on.
     * @param subId Subscribers ID.
     * @return {@code true} if radio on
     */
    public static boolean isRadioOn(int subId, Context context) {
        ITelephony phone = ITelephony.Stub.asInterface(ServiceManager
                .getService(Context.TELEPHONY_SERVICE));
        boolean isOn = false;
        if (phone != null) {
            try {
                isOn = subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID ? false
                        : phone.isRadioOnForSubscriber(subId, context.getPackageName());
            } catch (RemoteException e) {
                Log.e(TAG, "isRadioOn, RemoteException=" + e);
            }
        } else {
            Log.e(TAG, "isRadioOn, ITelephony is null.");
        }
        log("isRadioOn=" + isOn + ", subId=" + subId);
        return isOn;
    }

    /**
     * capability switch.
     * @return true : switching.
     */

    public static boolean isCapabilitySwitching() {
        IMtkTelephonyEx iTelEx = IMtkTelephonyEx.Stub.asInterface(
                ServiceManager.getService("phoneEx"));
        boolean isSwitching = false;
        if (iTelEx != null) {
            try {
                isSwitching = iTelEx.isCapabilitySwitching();
            } catch (RemoteException e) {
                Log.e(TAG, "isCapabilitySwitching, RemoteException=" + e);
            }
        } else {
            log("isCapabilitySwitching, IMtkTelephonyEx service not ready.");
        }
        log("isSwitching=" + isSwitching);
        return isSwitching;
    }

    private static void log(String msg){
        if (DBG) {
            Log.d(TAG, msg);
        }
    }

    /**
     * Get the phone id with main capability.
     */
    public static int getMainCapabilityPhoneId() {
        int phoneId = SubscriptionManager.INVALID_PHONE_INDEX;
        IMtkTelephonyEx iTelEx = IMtkTelephonyEx.Stub.asInterface(
                ServiceManager.getService("phoneEx"));
        if (iTelEx != null) {
            try {
                phoneId = iTelEx.getMainCapabilityPhoneId();
            } catch (RemoteException e) {
                log("getMainCapabilityPhoneId, RemoteException=" + e);
            }
        } else {
            log("IMtkTelephonyEx service not ready.");
            phoneId = RadioCapabilitySwitchUtil.getMainCapabilityPhoneId();
        }
        return phoneId;
    }

    /// M: Get the SIM lock policy/valid/capability. @{
    /**
     * Get the SIM lock policy.
     * @return the policy value.
     */
    public static int getSimLockPolicy() {
        int policy = MtkTelephonyManagerEx.getDefault().getSimLockPolicy();
        return policy;
    }

    /**
     * Get the SIM validity in SIM lock feature.
     * @param slotId the SIM slot ID.
     * @return the SIM validity.
     */
    public static int getSimLockSimValid(int slotId) {
        int valid = MtkTelephonyManagerEx.getDefault().checkValidCard(slotId);
        return valid;
    }

    /**
     * Get the SIM capability in SIM lock feature.
     * @param slotId the SIM slot ID.
     * @return the SIM capability.
     */
    public static int getSimLockSimCapability(int slotId) {
        int capa = MtkTelephonyManagerEx.getDefault().getShouldServiceCapability(slotId);
        return capa;
    }

    /**
     * Get the SIM case in SIM lock feature.
     * @param simSlotNum the SIM slot number.
     * @param simInserted the SIM inserted state array.
     * @param simValid the SIM validity array.
     * @return the SIM case ID.
     */
    public static int getSimLockCase(int simSlotNum, boolean[] simInserted,
            int[] simValid) {
        int countInserted = 0;
        int countUnknown = 0;
        int countValid = 0;
        int countInvalid = 0;
        int caseId = SIM_LOCK_CASE_NONE;

        for (int i = 0; i < simSlotNum; i++) {
            if (simInserted[i]) {
                countInserted++;
                switch (simValid[i]) {
                    case TelephonyUtils.SIM_LOCK_SIM_VALID_UNKNOWN:
                        countUnknown++;
                        break;

                    case TelephonyUtils.SIM_LOCK_SIM_VALID_NO:
                        countInvalid++;
                        break;

                    case TelephonyUtils.SIM_LOCK_SIM_VALID_YES:
                        countValid++;
                        break;

                    default:
                        countInserted--;
                        break;
                }
            }
        }

        if (countInserted == 0) {
            caseId = SIM_LOCK_CASE_ALL_EMPTY;
        } else if (countUnknown == countInserted) {
            caseId = SIM_LOCK_CASE_ALL_UNKNOWN;
        } else if (countValid == countInserted) {
            caseId = SIM_LOCK_CASE_ALL_VALID;
        } else if (countInvalid == countInserted) {
            caseId = SIM_LOCK_CASE_ALL_INVALID;
        } else if (countUnknown == 0) {
            if (countValid == 1) {
                caseId = SIM_LOCK_CASE_INVALID_1_VALID;
            } else {
                caseId = SIM_LOCK_CASE_INVALID_N_VALID;
            }
        } else if (countValid == 0) {
            caseId = SIM_LOCK_CASE_UNKNOWN_INVALID;
        } else if (countInvalid == 0) {
            if (countValid == 1) {
                caseId = SIM_LOCK_CASE_UNKNOWN_1_VALID;
            } else {
                caseId = SIM_LOCK_CASE_UNKNOWN_N_VALID;
            }
        } else if (countValid == 1) {
            caseId = SIM_LOCK_CASE_UNKNOWN_INVALID_1_VALID;
        } else {
            caseId = SIM_LOCK_CASE_UNKNOWN_INVALID_N_VALID;
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < simSlotNum; i++) {
            builder.append(", insert[" + i + "]=" + simInserted[i]
                    + ", valid[" + i + "]="
                    + TelephonyUtils.getSimLockSimValidString(simValid[i]));
        }
        log("getSimLockCase, case=" + getSimLockCaseString(caseId)
                + builder.toString());

        return caseId;
    }

    /**
     * Get the SIM lock policy string.
     * @param policy the policy ID.
     * @return the policy string.
     */
    public static String getSimLockPolicyString(int policy) {
        String str;
        switch (policy) {
            case SIM_LOCK_POLICY_UNKNOWN:
                str = "unknown";
                break;

            case SIM_LOCK_POLICY_NONE:
                str = "none";
                break;

            case SIM_LOCK_POLICY_SLOT1_ONLY:
                str = "1only";
                break;

            case SIM_LOCK_POLICY_SLOT2_ONLY:
                str = "2only";
                break;

            case SIM_LOCK_POLICY_SLOT_ALL:
                str = "all";
                break;

            case SIM_LOCK_POLICY_SLOT1_LINKED:
                str = "1lk";
                break;

            case SIM_LOCK_POLICY_SLOT2_LINKED:
                str = "2lk";
                break;

            case SIM_LOCK_POLICY_SLOT_ALL_LINKED:
                str = "alk";
                break;

            case SIM_LOCK_POLICY_SLOT_ALL_LINKED_WITH_CS:
                str = "alk_cs";
                break;

            case SIM_LOCK_POLICY_LEGACY:
                str = "legacy";
                break;

            default:
                str = "wrong";
                break;
        }

        return str + "(" + policy + ")";
    }

    /**
     * Get the SIM valid string in SIM lock feature.
     * @param valid the SIM valid ID.
     * @return the SIM valid string.
     */
    public static String getSimLockSimValidString(int valid) {
        String str;
        switch (valid) {
            case SIM_LOCK_SIM_VALID_UNKNOWN:
                str = "unknown";
                break;

            case SIM_LOCK_SIM_VALID_YES:
                str = "yes";
                break;

            case SIM_LOCK_SIM_VALID_NO:
                str = "no";
                break;

            case SIM_LOCK_SIM_VALID_ABSENT:
                str = "absent";
                break;

            default:
                str = "wrong";
                break;
        }

        return str + "(" + valid + ")";
    }

    /**
     * Get the SIM capability string in SIM lock feature.
     * @param capability the SIM capability ID.
     * @return the SIM capability string.
     */
    public static String getSimLockSimCapabilityString(int capability) {
        String str;
        switch (capability) {
            case SIM_LOCK_SIM_CAPABILITY_UNKNOWN:
                str = "unknown";
                break;

            case SIM_LOCK_SIM_CAPABILITY_FULL:
                str = "full";
                break;

            case SIM_LOCK_SIM_CAPABILITY_CS_ONLY:
                str = "cs_only";
                break;

            case SIM_LOCK_SIM_CAPABILITY_PS_ONLY:
                str = "ps_only";
                break;

            case SIM_LOCK_SIM_CAPABILITY_ECC_ONLY:
                str = "ecc_only";
                break;

            case SIM_LOCK_SIM_CAPABILITY_NO_SERVICE:
                str = "no_service";
                break;

            default:
                str = "wrong";
                break;
        }

        return str + "(" + capability + ")";
    }

    /**
     * Get the SIM Lock case string.
     * @param caseId the case ID.
     * @return the case string.
     */
    public static String getSimLockCaseString(int caseId) {
        String str;
        switch (caseId) {
            case SIM_LOCK_CASE_NONE:
                str = "none";
                break;

            case SIM_LOCK_CASE_ALL_EMPTY:
                str = "empty";
                break;

            case SIM_LOCK_CASE_ALL_UNKNOWN:
                str = "unknown";
                break;

            case SIM_LOCK_CASE_ALL_VALID:
                str = "valid";
                break;

            case SIM_LOCK_CASE_ALL_INVALID:
                str = "invalid";
                break;

            case SIM_LOCK_CASE_INVALID_1_VALID:
                str = "inv_1vld";
                break;

            case SIM_LOCK_CASE_INVALID_N_VALID:
                str = "inv_Nvld";
                break;

            case SIM_LOCK_CASE_UNKNOWN_INVALID:
                str = "unkwn_inv";
                break;

            case SIM_LOCK_CASE_UNKNOWN_1_VALID:
                str = "unkwn_1vld";
                break;

            case SIM_LOCK_CASE_UNKNOWN_N_VALID:
                str = "unkwn_Nvld";
                break;

            case SIM_LOCK_CASE_UNKNOWN_INVALID_1_VALID:
                str = "unkwn_inv_1vld";
                break;

            case SIM_LOCK_CASE_UNKNOWN_INVALID_N_VALID:
                str = "unkwn_inv_Nvld";
                break;

            default:
                str = "wrong";
                break;
        }

        return str + "(" + caseId + ")";
    }
    /// @}

}
