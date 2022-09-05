/*
 * Copyright (C) 2023 PixelOS
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

package com.android.systemui.qs.tiles;

import com.android.systemui.qs.tileimpl.QSTileImpl

import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey

@Module
interface CustomQSModule {

    /** Inject AODTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(AODTile.TILE_SPEC)
    fun bindAODTile(aodTile: AODTile): QSTileImpl<*>

    /** Inject CaffeineTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(CaffeineTile.TILE_SPEC)
    fun bindCaffeineTile(caffeineTile: CaffeineTile): QSTileImpl<*>

    /** Inject CompassTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(CompassTile.TILE_SPEC)
    fun bindCompassTile(compassTile: CompassTile): QSTileImpl<*>

    /** Inject CellularTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(CellularTile.TILE_SPEC)
    fun bindCellularTile(cellularTile: CellularTile): QSTileImpl<*>

    /** Inject DataSwitchTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(DataSwitchTile.TILE_SPEC)
    fun bindDataSwitchTile(dataSwitchTile: DataSwitchTile): QSTileImpl<*>

    /** Inject HeadsUpTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(HeadsUpTile.TILE_SPEC)
    fun bindHeadsUpTile(headsUpTile: HeadsUpTile): QSTileImpl<*>

    /** Inject LiveDisplayTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(LiveDisplayTile.TILE_SPEC)
    fun bindLiveDisplayTile(liveDisplayTile: LiveDisplayTile): QSTileImpl<*>

    /** Inject SoundTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(SoundTile.TILE_SPEC)
    fun bindSoundTile(soundTile: SoundTile): QSTileImpl<*>

    /** Inject SyncTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(SyncTile.TILE_SPEC)
    fun bindSyncTile(syncTile: SyncTile): QSTileImpl<*>

    /** Inject UsbTetherTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(UsbTetherTile.TILE_SPEC)
    fun bindUsbTetherTile(usbTetherTile: UsbTetherTile): QSTileImpl<*>

    /** Inject VpnTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(VpnTile.TILE_SPEC)
    fun bindVpnTile(vpnTile: VpnTile): QSTileImpl<*>

    /** Inject WifiTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(WifiTile.TILE_SPEC)
    fun bindWifiTile(wifiTile: WifiTile): QSTileImpl<*>
}
