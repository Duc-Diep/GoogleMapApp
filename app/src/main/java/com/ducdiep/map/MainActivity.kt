package com.ducdiep.map

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.PointF
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.here.android.mpa.common.*
import com.here.android.mpa.mapping.AndroidXMapFragment
import com.here.android.mpa.mapping.Map
import com.here.android.mpa.mapping.MapGesture.OnGestureListener
import com.here.android.mpa.mapping.MapMarker
import com.here.android.mpa.mapping.MapObject
import com.here.android.mpa.venues3d.VenueMapFragment
import java.io.File
import java.io.IOException
import java.util.*
import kotlin.collections.ArrayList


private const val REQUEST_CODE_ASK_PERMISSIONS = 1

private val REQUIRED_SDK_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE
)


class MainActivity : AppCompatActivity(), OnGestureListener {

    // map embedded in the map fragment
    private var mMap: Map? = null

    // map fragment embedded in this activity
    private var mapFragment: AndroidXMapFragment? = null

    private var lastPositionCenter:GeoCoordinate? = null

    private var mVenueMapFragment: VenueMapFragment? = null

    private var mActivity:MainActivity? = null

    private var mapObjectList: ArrayList<MapObject> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        checkPermissions()
    }

    fun initialize(){
        setContentView(R.layout.activity_main)
        mActivity = this

//        mapFragment = supportFragmentManager.findFragmentById(R.id.mapfragment) as AndroidXMapFragment?
        mVenueMapFragment = getMapFragment()
        com.here.android.mpa.common.MapSettings.setDiskCacheRootPath(
            "${applicationContext.getExternalFilesDir(null)}${File.separator}.here-maps")
        mVenueMapFragment?.init {
            if (it== OnEngineInitListener.Error.NONE){
                // Adding venue listener to map fragment
//        mVenueMapFragment!!.addListener(mActivity)
                // Set animations on for floor change and venue entering
                // Set animations on for floor change and venue entering
                mVenueMapFragment!!.setFloorChangingAnimation(true)
                mVenueMapFragment!!.setVenueEnteringAnimation(true)
                // Ask notification when venue visible; this notification is
                // part of VenueMapFragment.VenueListener
                // Ask notification when venue visible; this notification is
                // part of VenueMapFragment.VenueListener
                mVenueMapFragment!!.setVenuesInViewportCallback(true)

                // Add Gesture Listener for map fragment

                // Add Gesture Listener for map fragment
                mVenueMapFragment!!.mapGesture!!.addOnGestureListener(mActivity!!, 0, false)

                // retrieve a reference of the map from the map fragment

                // retrieve a reference of the map from the map fragment
                mMap = mVenueMapFragment!!.map



//                mMap = mapFragment!!.map
                // Set the map center to the Vancouver region (no animation)
                // Set the map center to the Vancouver region (no animation)
                mMap!!.setCenter(
                    GeoCoordinate(21.053736, 105.7329181, 17.0),
                    Map.Animation.NONE
                )
//                mMap!!.mapScheme = Map.Scheme.HYBRID_DAY
                // Set the zoom level to the average between min and max
                // Set the zoom level to the average between min and max
                mMap!!.zoomLevel = (mMap!!.maxZoomLevel + mMap!!.minZoomLevel) / 2
            }else{
                Toast.makeText(this, "Error when load map", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getMapFragment(): VenueMapFragment? {
        return fragmentManager.findFragmentById(R.id.mapfragment) as VenueMapFragment
    }

    private fun addMarkerAtPlace(geoCoordinate: GeoCoordinate) {
        val img = Image()
        try {
            img.setImageResource(R.drawable.marker)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        val mapMarker = MapMarker()
        mapMarker.icon = img
        mapMarker.coordinate = geoCoordinate
        mMap!!.addMapObject(mapMarker)
        mapObjectList.add(mapMarker)
    }

    private fun cleanMap() {
        if (!mapObjectList.isEmpty()) {
            mMap!!.removeMapObjects(mapObjectList)
            mapObjectList.clear()
        }
    }




    fun checkPermissions() {
        val missingPermissions: MutableList<String> =
            ArrayList()
        // check all required dynamic permissions
        for (permission in REQUIRED_SDK_PERMISSIONS) {
            val result = ContextCompat.checkSelfPermission(this, permission)
            if (result != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission)
            }
        }
        if (!missingPermissions.isEmpty()) {
            // request all missing permissions
            val permissions = missingPermissions
                .toTypedArray()
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_ASK_PERMISSIONS)
        } else {
            val grantResults = IntArray(REQUIRED_SDK_PERMISSIONS.size)
            Arrays.fill(grantResults, PackageManager.PERMISSION_GRANTED)
            onRequestPermissionsResult(
                REQUEST_CODE_ASK_PERMISSIONS, REQUIRED_SDK_PERMISSIONS,
                grantResults
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            REQUEST_CODE_ASK_PERMISSIONS -> {
                var index = permissions.size - 1
                while (index >= 0) {
                    if (grantResults[index] != PackageManager.PERMISSION_GRANTED) {
                        // exit the app if one permission is not granted
                        Toast.makeText(
                            this, "Required permission '" + permissions[index]
                                    + "' not granted, exiting", Toast.LENGTH_LONG
                        ).show()
                        finish()
                        return
                    }
                    --index
                }
                // all permissions were granted
                initialize()
            }
        }
    }

    override fun onLongPressRelease() {

    }

    override fun onRotateEvent(p0: Float): Boolean {
        return false
    }

    override fun onMultiFingerManipulationStart() {

    }

    override fun onPinchLocked() {

    }

    override fun onPinchZoomEvent(p0: Float, p1: PointF): Boolean {
        return false
    }

    override fun onTapEvent(p: PointF): Boolean {
        if (mMap == null) {
            Toast.makeText(this, "Initialization of venue service is in progress...", Toast.LENGTH_SHORT).show()
            return false
        }
        val touchLocation: GeoCoordinate = mMap!!.pixelToGeo(p)!!
        mMap!!.setCenter(
                touchLocation,
                Map.Animation.NONE
        )
        return false
    }

    override fun onPanStart() {

    }

    override fun onMultiFingerManipulationEnd() {

    }

    override fun onDoubleTapEvent(p0: PointF): Boolean {
        return false
    }

    override fun onPanEnd() {

    }

    override fun onTiltEvent(p0: Float): Boolean {
        return false
    }

    override fun onMapObjectsSelected(p0: MutableList<ViewObject>): Boolean {
        return false
    }

    override fun onRotateLocked() {

    }

    //long click
    override fun onLongPressEvent(p: PointF): Boolean {
        if (mMap == null) {
            Toast.makeText(this, "Initialization of venue service is in progress...", Toast.LENGTH_SHORT).show()
            return false
        }
        cleanMap()
        val touchLocation: GeoCoordinate = mMap!!.pixelToGeo(p)!!
        val lat = touchLocation.latitude
        val lon = touchLocation.longitude
        val strGeo = String.format("%.6f, %.6f", lat, lon)
        addMarkerAtPlace(touchLocation)
        Toast.makeText(this, "$strGeo", Toast.LENGTH_SHORT).show()
        return false
    }


    override fun onTwoFingerTapEvent(p0: PointF): Boolean {
        return false
    }
}