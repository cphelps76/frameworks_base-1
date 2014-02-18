/******************************************************************************
 *  This program is protected under international and U.S. copyright laws as
 *  an unpublished work. This program is confidential and proprietary to the
 *  copyright owners. Reproduction or disclosure, in whole or in part, or the
 *  production of derivative works therefrom without the express permission of
 *  the copyright owners is prohibited.
 *
 *               Copyright (C) 2011-2012 by Dolby Laboratories,
 *                             All rights reserved.
 ******************************************************************************/

/*
 * DsProfileSettings.java
 *
 * Internal class that encapsulates DsAkSettings that represent a user-selectable profile.
 */


package android.dolby.ds;

import java.util.*;
import android.util.Log;

import android.dolby.DsClientSettings;
import android.dolby.DsConstants;
import android.dolby.DsLog;

/**
 * A group of Ds AK settings that can be applied to the DsEffect effect.
 */
public class DsProfileSettings
{
    /**
     * Categories.
     *
     * Each profile can be categorised into one of these categories. The
     * Ds manages the profiles and provides methods to retrieve
     * all profiles or only those profiles belonging to a specified category.
     *
     * The categories are defined as integers starting from 0, to
     * facilitate writing code and loops based on integers. Helper methods
     * allow conversion from Category to and from the integer.
     */
    public enum Category
    {
        MUSIC(0), ///< Music Category.
        MOVIE(1), ///< Movie and Video Category.
        GAME(2),  ///< Game Category.
        VOICE(3), ///< Voice (e.g. VOIP) Category.
        CUSTOMIZED(4); ///< User Defined Category.
        /**
         * The number of categories.
         *
         * Useful to limit a loop based on the integer representation
         * of the categories.
         */
        static public final int COUNT = 5;
        /**
         * @return The integer representation of this Category.
         */
        public int toInt() { return value_; }
        /**
         * @return A string representing the English name of this Category.
         */
        public String toString() { return NAME[value_]; }
        /**
         * @param i The integer representation which Category is being retrieved.
         * @return The Category that represents the specified integer.
         * An exception will be thrown if the integer does not map to a Category.
         */
        static public Category FromInt(int i) { return CATEGORY[i]; }
        static private final String NAME[] = {"Music", "Movie", "Game", "Voice", "Customized"};
        static private final Category CATEGORY[] = {MUSIC, MOVIE, GAME, VOICE, CUSTOMIZED};
        private int value_;
        private Category(int value) { this.value_ = value; }
    }

    /**
     * The scaling factor for the conversion between the gain values and the dB values.
     */
    public static final int DB_SCALING_FACTOR = 16;

    /**
     * The graphic equalizer band gains for each intelligent equalizer preset.
     * @internal
     */
    private short geqBandGains_[][] = null;

    /**
     * The intelligent equalizer band targets for each intelligent equalizer preset.
     * @internal
     */
    static private short ieqBandTargets_[][] = null;

    /**
     * The (short) name of this profile suitable for display purposes.
     * @internal
     */
    private String displayName_;

    /**
     * Longer description of the profile.
     * @internal
     */
    private String description_;

    /**
     * Whether the profile is a customized profile or not.
     * @internal
     */
    private boolean custom_;

    /**
     * The category of the profile.
     * @internal
     */
    private Category category_;

    /**
     * The intelligent equalizer preset currently adopted.
     * @internal
     */
    private int currentIeqPreset_;

    /**
     * DS settings for one or more audio devices, representing this profile.
     * @internal
     */
    private HashMap<AudioDevice, DsAkSettings> allSettings_;

    /**
     * All the parameters that will be saved before shutdown.
     */
    private HashSet<String> profileParamsToBeSaved_;

    private static final String TAG = "DsProfileSettings";

    /**
     * Non-public constructor - declared package-private (no access modifier).
     * @internal
     * @param displayName   Short display name for the effect.
     * @param description   Description for the effect.
     * @param allSettings   All settings for one or more DsEndpoint instances.
     * @param custom        Whether the effect shall be customizable.
     * @param category      The category that the effect shall belong to.
     * @param ieqPreset     The initial Ieq preset index.
     * @param savedParams   The parameters that will be saved before shutdown.
     * @throws InstantiationException
     * @throws IllegalArgumentException
     */
    DsProfileSettings(String displayName, String description, Map<DsEndpoint, DsAkSettings> allSettings, boolean custom, Category category, int ieqPreset, int[][] geqSettings, HashSet<String> savedParams)
         throws InstantiationException, IllegalArgumentException
    {
        if (!DsAkSettings.isConstantAkParamsDefined())
            throw new InstantiationException("Constant AK parameters NOT defined yet.");
        if (ieqBandTargets_ == null)
            throw new InstantiationException("IEq settings NOT defined yet.");
        displayName_ = displayName;
        description_ = description;
        custom_ = custom;
        category_ = category;

        allSettings_ = new HashMap<AudioDevice, DsAkSettings>();
        //Convert DsEndpoint to AudioDevice.
        for (DsEndpoint endpoint : allSettings.keySet())
            allSettings_.put(endpoint.toDevice(), allSettings.get(endpoint));

        int gebgLen = DsAkSettings.getGeqBandCount();
        geqBandGains_ = new short[DsConstants.IEQ_PRESETS_NUMBER][gebgLen];
        if (geqSettings != null)
        {
            if (geqSettings.length != DsConstants.IEQ_PRESETS_NUMBER || geqSettings[0].length != gebgLen)
            {
                Log.e(TAG, "Wrong array length for GEq settings, check whether the length conforms to genb in the XML file");
                throw new IllegalArgumentException("GEq settings array length is invalid");
            }
            for (int i = 0; i < DsConstants.IEQ_PRESETS_NUMBER; i++)
            {
                for (int j = 0; j < gebgLen; j++)
                {
                    geqBandGains_[i][j] = (short)geqSettings[i][j];
                }
            }
        }
        else
        {
            for (int i = 0; i < DsConstants.IEQ_PRESETS_NUMBER; i++)
            {
                for (int j = 0; j < gebgLen; j++)
                {
                    geqBandGains_[i][j] = 0;
                }
            }
        }

        DsAkSettings akSettings = allSettings_.get(AudioDevice.DEVICE_WIRED_HEADPHONE);
        short ieqOn = akSettings.get("ieon", 0);
        currentIeqPreset_ = (ieqOn == DsAkSettings.AK_DS1_FEATURE_OFF) ? DsConstants.IEQ_PRESET_OFF :
                            (ieqPreset == DsConstants.IEQ_PRESET_OFF) ? DsConstants.IEQ_PRESET_OPEN : ieqPreset;

        for (int i = 0; i < gebgLen; ++i)
        {
            akSettings.set("gebg", i, geqBandGains_[currentIeqPreset_][i]);
        }

        int iebtLen = DsAkSettings.getParamArrayLength("iebt");
        for (int i = 0; i < iebtLen; ++i)
        {
            akSettings.set("iebt", i, ieqBandTargets_[currentIeqPreset_][i]);
        }

        profileParamsToBeSaved_ = savedParams;
    }

    /**
     * Retrieve all settings to be passed to the underlying Ds effect.
     *
     * Note that the settings are stored and returned mapped an AudioDevice, not DsEndpoint.
     *
     * Declared package-private (no access modifier).
     * @return All the settings for all audio devices.
     * @internal
     */
    Map<AudioDevice, DsAkSettings> getAllSettings()
    {
        return allSettings_;
    }

    /**
     * @return The (short) name of this profile suitable for display purposes.
     */
    @Override
    public String toString()
    {
        return displayName_;
    }

    /**
     * Get the display name of this profile.
     *
     * @return The (short) name of this profile suitable for display purposes.
     */
    public String getDisplayName()
    {
        return displayName_;
    }

    /**
     * Get the description of this profile.
     *
     * @return The description of this profile.
     */
    public String getDescription()
    {
        return description_;
    }

    /**
     * Tell whether this profile is a customized profile or not.
     *
     * @return true if this profile is a customized profile that can be modified. false otherwise.
     */
    public boolean isCustom()
    {
        return custom_;
    }

    /**
     * Get the category of this profile.
     *
     * @return The category of this profile.
     */
    public Category getCategory()
    {
        return category_;
    }

    /**
     * @return The GEQ gain array.
     */
    public short[][] getGeqGainArray()
    {
        return geqBandGains_;
    }

    /**
     * Get the current profile settings for the specified parameters. These represent the current settings that need to be saved.
     *
     * @return The current profile settings string.
     */
    public String getCurrentProfileSettings()
    {
        DsAkSettings akSettings = allSettings_.get(AudioDevice.DEVICE_WIRED_HEADPHONE);
        String settingStr = null;

        if (profileParamsToBeSaved_ != null)
        {
            settingStr = "";
            // populate profile setting string for the current xml.
            Iterator iter = profileParamsToBeSaved_.iterator();
            while (iter.hasNext())
            {
                String param = (String)iter.next();
                short value = akSettings.get(param, 0);
                int len = DsAkSettings.getParamArrayLength(param);
                settingStr += (param + "=[" + String.valueOf(value));
                for (int j = 1; j < len; j++)
                {
                    value = akSettings.get(param, j);
                    settingStr += (", " + String.valueOf(value));
                }
                if (iter.hasNext())
                    settingStr += ("] ");
                else
                    settingStr += ("]");
            }
        }
        return settingStr;
    }

    /**
     * Copy all Ds AK settings from the specified profile to another
     * profile.
     *
     * Only Ds AK settings are copied. Other details such as the
     * display name, description, category etc are not copied.
     *
     * @param source The profile to copy settings from.
     */
    public void newFrom(DsProfileSettings source)
    {
        Map<AudioDevice, DsAkSettings> allSettings = source.getAllSettings();
        allSettings_ = new HashMap<AudioDevice, DsAkSettings>();
        for (AudioDevice ad : allSettings.keySet())
            allSettings_.put(ad, new DsAkSettings(allSettings.get(ad)));
    }

    /**
     * Copy all Ds AK settings from the specified profile to another
     * profile.
     *
     * Only Ds AK settings are copied. Other details such as the
     * display name, description, category etc are not copied.
     *
     * @param profileSpec The profile specification to copy settings from.
     */
    public void newFrom(String profileSpec)
    {
        // TODO: Implement the method once the solution how the profile/settings
        // will be retrieved from file is finalized
    }

    /**
     * Set the display name of this custom profile.
     * This can only be performed on custom profiles.
     *
     * @param displayName The new display name.
     */
    public void setDisplayName(String displayName)
    {
        if (isCustom())
        {
            displayName_ = displayName;
        }
    }

    /**
     * Set the description of this custom profile.
     * This can only be performed on custom profiles.
     *
     * @param description The new display name.
     */
    public void setDescription(String description)
    {
        if (isCustom())
        {
            description_ = description;
        }
    }

    /**
     * Initialize the Ieq band targets for each Ieq preset.
     * NOTE: All the Ieq band targets must be initialized before we create a DsProfileSettings instance.
     *
     * @param ieqPreset The Ieq preset index.
     * @param values    The Ieq band target values for the specified IEQ preset.
     * @throws IllegalArgumentException
     * @throws UnsupportedOperationException
     */
    static public void setIeqBandTargets(int ieqPreset, int[] values) throws IllegalArgumentException, UnsupportedOperationException
    {
        if (ieqPreset < 0 || ieqPreset >= DsConstants.IEQ_PRESETS_NUMBER)
            throw new IllegalArgumentException("Invalid Intelligent Equalizer preset index!");
        if (!DsAkSettings.isConstantAkParamsDefined())
            throw new UnsupportedOperationException("Constant AK parameters NOT defined yet.");
        int iebtLen = DsAkSettings.getParamArrayLength("iebt");
        if (values.length != iebtLen)
        {
            Log.e(TAG, "Invalid count of IEq values, check whether iebt array length conforms to ienb in the XML file");
            throw new IllegalArgumentException("The count of IEq values NOT equal to the IEq band count");
        }
        if (ieqBandTargets_ == null)
            ieqBandTargets_ = new short[DsConstants.IEQ_PRESETS_NUMBER][iebtLen];
        for (int i = 0; i < iebtLen; i++)
            ieqBandTargets_[ieqPreset][i] = (short)values[i];
    }

    /**
     * Returns the client settings.
     * TODO: Since all the endpoints share the same AK settings, we only take the AK settings of the speaker endpoint. If
     * the customer raise a requirement that the different endpoints must keep different AK settings, we will differentiate
     * the AK settings for different endpoints.
     *
     * @return The client settings adopted currently.
     */
    public DsClientSettings extractClientSettings()
    {
        DsAkSettings akSettings = allSettings_.get(AudioDevice.DEVICE_WIRED_HEADPHONE);

        // Create a DsClientSettings instance.
        DsClientSettings clientSettings = new DsClientSettings();

        // Set the client settings with the values retrieved from the AK settings.
        boolean isGeqOn = (akSettings.get("geon", 0) != DsAkSettings.AK_DS1_FEATURE_OFF ) ? true : false;
        clientSettings.setGeqOn(isGeqOn);
        boolean isDialogEnhancerOn = (akSettings.get("deon", 0) != DsAkSettings.AK_DS1_FEATURE_OFF ) ? true : false;
        clientSettings.setDialogEnhancerOn(isDialogEnhancerOn);
        boolean isVolumeLevellerOn = (akSettings.get("dvle", 0) != DsAkSettings.AK_DS1_FEATURE_OFF ) ? true : false;
        clientSettings.setVolumeLevellerOn(isVolumeLevellerOn);
        boolean isHeadphoneVirtualizerOn = (akSettings.get("vdhe", 0) != DsAkSettings.AK_DS1_FEATURE_OFF ) ? true : false;
        clientSettings.setHeadphoneVirtualizerOn(isHeadphoneVirtualizerOn);
        boolean isSpeakerVirtualizerOn = (akSettings.get("vspe", 0) != DsAkSettings.AK_DS1_FEATURE_OFF ) ? true : false;
        clientSettings.setSpeakerVirtualizerOn(isSpeakerVirtualizerOn);

        return clientSettings;
    }

    /**
     * Update the current settings with the ones passed from the client.
     * TODO: Since all the endpoints share the same AK settings on a certain profile, we only take the AK settings of the
     * speaker endpoint. If the customer raise a requirement that the different endpoints must keep different AK settings,
     * we will differentiate the AK settings for different endpoints.
     *
     * @param clientSettings The new client settings.
     * @return The list containing all the AK parameters with value/values change.
     */
    public ArrayList updateFromClientSettings(DsClientSettings clientSettings)
    {
        DsAkSettings akSettings = allSettings_.get(AudioDevice.DEVICE_WIRED_HEADPHONE);

        boolean isGeqBandGainsChanged = false;
        ArrayList<String> paramsChanged = new ArrayList<String>();

        // Set the AK settings with the values passed from the client settings.
        short geqOn = clientSettings.getGeqOn() ? DsAkSettings.AK_DS1_FEATURE_ON : DsAkSettings.AK_DS1_FEATURE_OFF;
        if (geqOn != akSettings.get("geon", 0))
        {
            akSettings.set("geon", 0, geqOn);
            paramsChanged.add("geon");
        }

        short dialogEnhancerOn = clientSettings.getDialogEnhancerOn() ? DsAkSettings.AK_DS1_FEATURE_ON : DsAkSettings.AK_DS1_FEATURE_OFF;
        if (dialogEnhancerOn != akSettings.get("deon", 0))
        {
            akSettings.set("deon", 0, dialogEnhancerOn);
            paramsChanged.add("deon");
        }

        short volumeLevellerOn = clientSettings.getVolumeLevellerOn() ? DsAkSettings.AK_DS1_FEATURE_ON : DsAkSettings.AK_DS1_FEATURE_OFF;
        if (volumeLevellerOn != akSettings.get("dvle", 0))
        {
            akSettings.set("dvle", 0, volumeLevellerOn);
            paramsChanged.add("dvle");
        }

        short headphoneVirtualizerOn = clientSettings.getHeadphoneVirtualizerOn() ? DsAkSettings.AK_DS1_FEATURE_AUTO : DsAkSettings.AK_DS1_FEATURE_OFF;
        if (headphoneVirtualizerOn != akSettings.get("vdhe", 0))
        {
            akSettings.set("vdhe", 0, headphoneVirtualizerOn);
            paramsChanged.add("vdhe");
        }

        short speakerVirtualizerOn = clientSettings.getSpeakerVirtualizerOn() ? DsAkSettings.AK_DS1_FEATURE_AUTO : DsAkSettings.AK_DS1_FEATURE_OFF;
        if (speakerVirtualizerOn != akSettings.get("vspe", 0))
        {
            akSettings.set("vspe", 0, speakerVirtualizerOn);
            paramsChanged.add("vspe");
        }

        return paramsChanged;
    }

    /**
     * Update the Ieq preset.
     *
     * @param preset The index of the new preset.
     */
    public void setIeqPreset(int preset)
    {
        DsAkSettings akSettings = allSettings_.get(AudioDevice.DEVICE_WIRED_HEADPHONE);

        if (preset != currentIeqPreset_)
        {
            akSettings.set("ieon", 0, (preset != 0) ? DsAkSettings.AK_DS1_FEATURE_ON : DsAkSettings.AK_DS1_FEATURE_OFF);
            int iebtLen = DsAkSettings.getParamArrayLength("iebt");
            for (int i = 0; i < iebtLen; ++i)
            {
                akSettings.set("iebt", i, ieqBandTargets_[preset][i]);
            }
            int gebgLen = DsAkSettings.getGeqBandCount();
            for (int i = 0; i < gebgLen; ++i)
            {
                akSettings.set("gebg", i, geqBandGains_[preset][i]);
            }
            currentIeqPreset_ = preset;
        }
        else
        {
            DsLog.log2(TAG, "Set the same Ieq value " + preset + " as last time, nothing will be done.");
        }
    }

    /**
     * Get the active Ieq preset.
     *
     * @return The active Ieq preset of current profile.
     */
    public int getIeqPreset()
    {
        return currentIeqPreset_;
    }

   /**
    * Set the Geq band gains of specified preset.
    *
    * @param preset The index of the Ieq preset.
    * @param gains The new Geq band gains.
    *
    * @return The scaled Geq band gains.
    */
    public short[] setGeq(int preset, float[] gains)
    {
        DsAkSettings akSettings = allSettings_.get(AudioDevice.DEVICE_WIRED_HEADPHONE);

        int gebfLen = DsAkSettings.getParamArrayLength("gebf");
        short[] values = new short[gebfLen];
        for (int i = 0; i < gebfLen; i++)
        {
            values[i] = (short)(DsProfileSettings.DB_SCALING_FACTOR * gains[i]);
            akSettings.set("gebg", i, values[i]);
            // store new Geq band gains, then it can be stored into xml files
            geqBandGains_[preset][i] = values[i];
        }
        return values;
    }

    /**
    * Get the Geq band gains of specified preset.
    *
    * @param preset The index of the Ieq preset.
    *
    * @return The Geq band gains.
    */
    public float[] getGeq(int preset)
    {
        DsAkSettings akSettings = allSettings_.get(AudioDevice.DEVICE_WIRED_HEADPHONE);

        int gebfLen = DsAkSettings.getParamArrayLength("gebf");
        float[] values = new float[gebfLen];
        for (int i = 0; i < gebfLen; i++)
        {
            values[i] = (float)akSettings.get("gebg", i) / DsProfileSettings.DB_SCALING_FACTOR;
        }
        return values;
    }

    /**
     * Set a ds audio processing parameter.
     *
     * @param parameter The parameter name.
     * @param values The parameter values.
     *
     * @throws UnsupportedOperationException if the set operation fails.
     */
    public void setDsApParam(String parameter, short[] values) throws UnsupportedOperationException
    {
        if (!DsAkSettings.akSettableParamDefinitions.contains(parameter))
        {
            Log.e(TAG, "the parameter " + parameter + " is NOT settable.");
            throw new UnsupportedOperationException("Invalid parameter");
        }
        int len = DsAkSettings.getParamArrayLength(parameter);
        if (values.length != len)
        {
            Log.e(TAG, "the values length " + values.length + " is NOT compatible with the desired length " + len);
            throw new UnsupportedOperationException("Invalid values length");
        }

        DsAkSettings akSettings = allSettings_.get(AudioDevice.DEVICE_WIRED_HEADPHONE);
        for (int i = 0; i < values.length; ++i)
        {
            akSettings.set(parameter, i, values[i]);
        }
    }

    /**
     * Get the current value of the specified parameter.
     *
     * @param parameter  The parameter name.
     * @return The values currently adopted by the specified parameter.
     *
     * @throws UnsupportedOperationException if the get operation fails.
     */
    public int[] getDsApParam(String parameter) throws UnsupportedOperationException
    {
        if (!DsAkSettings.akSettableParamDefinitions.contains(parameter))
        {
            Log.e(TAG, "the parameter " + parameter + " is NOT retrievable.");
            throw new UnsupportedOperationException("Invalid parameter");
        }

        DsAkSettings akSettings = allSettings_.get(AudioDevice.DEVICE_WIRED_HEADPHONE);
        int[] values = new int[DsAkSettings.getParamArrayLength(parameter)];
        for (int i = 0; i < values.length; i++)
        {
            values[i] = (int)akSettings.get(parameter, i);
        }
        return values;
    }

    /**
     * Get all the parameters that must be saved before shutdown.
     *
     * @return The array containing all the parameters that must be saved.
     */
    public Object[] getParamsSaved()
    {
        if (profileParamsToBeSaved_ != null)
            return profileParamsToBeSaved_.toArray();
        else
            return null;
    }

    /**
     * Add a new parameter that is necessary to be saved before shutdown.
     *
     * @param parameter The parameter name.
     */
    public void addParamSaved(String parameter)
    {
        if (profileParamsToBeSaved_ != null && profileParamsToBeSaved_.add(parameter))
        {
            DsLog.log1(TAG, "Add a new parameter " + parameter + " to the saved list");
        }
    }

    /**
     * Remove a parameter that is not necessary to be saved before shutdown.
     *
     * @param parameter The parameter name.
     */
    public void removeParamSaved(String parameter)
    {
        if (profileParamsToBeSaved_ != null && profileParamsToBeSaved_.remove(parameter))
        {
            DsLog.log1(TAG, "Remove the parameter " + parameter + " from the saved list");
        }
    }
}
