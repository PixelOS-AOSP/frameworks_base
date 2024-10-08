/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.internal.pm.pkg.component;

import static com.android.internal.pm.parsing.pkg.PackageImpl.sForInternedString;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ComponentName;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.DataClass;
import com.android.internal.util.Parcelling.BuiltIn.ForInternedString;

/** @hide **/
@DataClass(genSetters = true, genGetters = true, genParcelable = false, genBuilder = false)
@VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
public class ParsedServiceImpl extends ParsedMainComponentImpl implements ParsedService,
        Parcelable {

    private int foregroundServiceType;
    @Nullable
    @DataClass.ParcelWith(ForInternedString.class)
    private String permission;

    public ParsedServiceImpl(ParsedServiceImpl other) {
        super(other);
        this.foregroundServiceType = other.foregroundServiceType;
        this.permission = other.permission;
    }

    public ParsedMainComponent setPermission(String permission) {
        // Empty string must be converted to null
        this.permission = TextUtils.isEmpty(permission) ? null : permission.intern();
        return this;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder(128);
        sb.append("Service{");
        sb.append(Integer.toHexString(System.identityHashCode(this)));
        sb.append(' ');
        ComponentName.appendShortString(sb, getPackageName(), getName());
        sb.append('}');
        return sb.toString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(this.foregroundServiceType);
        sForInternedString.parcel(this.permission, dest, flags);
    }

    public ParsedServiceImpl() {
    }

    protected ParsedServiceImpl(Parcel in) {
        super(in);
        this.foregroundServiceType = in.readInt();
        this.permission = sForInternedString.unparcel(in);
    }

    @NonNull
    public static final Parcelable.Creator<ParsedServiceImpl> CREATOR =
            new Parcelable.Creator<ParsedServiceImpl>() {
                @Override
                public ParsedServiceImpl createFromParcel(Parcel source) {
                    return new ParsedServiceImpl(source);
                }

                @Override
                public ParsedServiceImpl[] newArray(int size) {
                    return new ParsedServiceImpl[size];
                }
            };



    // Code below generated by codegen v1.0.23.
    //
    // DO NOT MODIFY!
    // CHECKSTYLE:OFF Generated code
    //
    // To regenerate run:
    // $ codegen $ANDROID_BUILD_TOP/frameworks/base/core/java/com/android/internal/pm/pkg/component/ParsedServiceImpl.java
    //
    // To exclude the generated code from IntelliJ auto-formatting enable (one-time):
    //   Settings > Editor > Code Style > Formatter Control
    //@formatter:off


    @DataClass.Generated.Member
    public ParsedServiceImpl(
            int foregroundServiceType,
            @Nullable String permission) {
        this.foregroundServiceType = foregroundServiceType;
        this.permission = permission;

        // onConstructed(); // You can define this method to get a callback
    }

    @DataClass.Generated.Member
    public int getForegroundServiceType() {
        return foregroundServiceType;
    }

    @DataClass.Generated.Member
    public @Nullable String getPermission() {
        return permission;
    }

    @DataClass.Generated.Member
    public @NonNull ParsedServiceImpl setForegroundServiceType( int value) {
        foregroundServiceType = value;
        return this;
    }

    @DataClass.Generated(
            time = 1701445638370L,
            codegenVersion = "1.0.23",
            sourceFile = "frameworks/base/core/java/com/android/internal/pm/pkg/component/ParsedServiceImpl.java",
            inputSignatures = "private  int foregroundServiceType\nprivate @android.annotation.Nullable @com.android.internal.util.DataClass.ParcelWith(com.android.internal.util.Parcelling.BuiltIn.ForInternedString.class) java.lang.String permission\npublic static final @android.annotation.NonNull android.os.Parcelable.Creator<com.android.internal.pm.pkg.component.ParsedServiceImpl> CREATOR\npublic  com.android.internal.pm.pkg.component.ParsedMainComponent setPermission(java.lang.String)\npublic  java.lang.String toString()\npublic @java.lang.Override int describeContents()\npublic @java.lang.Override void writeToParcel(android.os.Parcel,int)\nclass ParsedServiceImpl extends com.android.internal.pm.pkg.component.ParsedMainComponentImpl implements [com.android.internal.pm.pkg.component.ParsedService, android.os.Parcelable]\n@com.android.internal.util.DataClass(genSetters=true, genGetters=true, genParcelable=false, genBuilder=false)")
    @Deprecated
    private void __metadata() {}


    //@formatter:on
    // End of generated code

}
