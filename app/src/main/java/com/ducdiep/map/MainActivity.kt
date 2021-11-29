package com.ducdiep.map

import android.Manifest
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PointF
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.SearchView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import com.ducdiep.map.adapters.AutoSuggestAdapter
import com.here.android.mpa.common.*
import com.here.android.mpa.mapping.*
import com.here.android.mpa.mapping.Map
import com.here.android.mpa.mapping.MapGesture.OnGestureListener
import com.here.android.mpa.routing.*
import com.here.android.mpa.search.*
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.IOException
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.toTypedArray


private const val REQUEST_CODE_ASK_PERMISSIONS = 1

private val REQUIRED_SDK_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.WRITE_EXTERNAL_STORAGE,
    Manifest.permission.ACCESS_COARSE_LOCATION
)


class MainActivity : AppCompatActivity(), OnGestureListener, LocationListener {

    private var mMap: Map? = null
    private var mapFragment: AndroidXMapFragment? = null
    private lateinit var mapFragmentView: View
    private var currentPosition: Location? = null
    private var mActivity: MainActivity? = null
    private var mapObjectList: ArrayList<MapObject> = ArrayList()
    private var listAutoSuggest: MutableList<AutoSuggest> = ArrayList()
    private lateinit var autoSuggestAdapter: AutoSuggestAdapter
    private lateinit var searchListener: SearchListener
    private var endPointLocation: GeoCoordinate? = null
    private var mapRoute: MapRoute? = null
    private var currentTranportId = 0
    lateinit var transportMode: RouteOptions.TransportMode
    var isCalculated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        checkPermissions()
    }

    //init all
    fun initialize() {
        setContentView(R.layout.activity_main)
        mActivity = this
        mapFragment =
            supportFragmentManager.findFragmentById(R.id.mapfragment) as AndroidXMapFragment?
        mapFragmentView = findViewById(R.id.mapfragment)
        if (mapFragment != null) {
            //set disk cache
            MapSettings.setDiskCacheRootPath(
                "${applicationContext.getExternalFilesDir(null)}${File.separator}.here-maps"
            )
            //get current location
            getCurrentPosisition()
            Log.d("Locationn", "initialize: $currentPosition")

            // init mapframent
            mapFragment?.init {
                if (it == OnEngineInitListener.Error.NONE) {//no error
                    mapFragment!!.mapGesture!!.addOnGestureListener(mActivity!!, 0, false)
                    mMap = mapFragment!!.map

                    if (currentPosition != null) {
                        moveToCurrentPosition()
                    } else {
                        mMap!!.setCenter(
                            GeoCoordinate(21.05401, 105.73507), Map.Animation.NONE
                        )
                        addMarkerAtPlace(
                            GeoCoordinate(21.05401, 105.73507),
                            R.drawable.location_marker
                        )
                    }

//                mMap!!.mapScheme = Map.Scheme.HYBRID_DAY
                    //zoom lv
                    mMap!!.zoomLevel = 12.0
                } else {
                    Toast.makeText(this, "Error when load map", Toast.LENGTH_SHORT).show()
                }
            }
        }

        //set up search view
        searchListener = SearchListener()
        search_view.setOnQueryTextListener(searchListener)

        //setup adapter search
        autoSuggestAdapter = AutoSuggestAdapter(this, listAutoSuggest)
        autoSuggestAdapter.setOnClickItem {
            handleSelectedAutoSuggest(it)
//            Toast.makeText(this, "$it", Toast.LENGTH_SHORT).show()
        }

        val dividerItemDecoration = DividerItemDecoration(
            this,
            RecyclerView.VERTICAL
        )
        rcv_resutl.adapter = autoSuggestAdapter
        rcv_resutl.addItemDecoration(dividerItemDecoration)

        //set on click button
        btn_gps.setOnClickListener {
            moveToCurrentPosition()
        }

        btn_direct.setOnClickListener {
            if (endPointLocation !== null) {
                showDialogTransport()
            } else {
                Toast.makeText(this, "Chưa chọn điểm đến", Toast.LENGTH_SHORT).show()
            }
        }

        btn_swap.setOnClickListener {
            if (isCalculated){
                calculateRoute(endPointLocation!!,GeoCoordinate(currentPosition!!.latitude,currentPosition!!.longitude))
            }else{
                Toast.makeText(this, "Chưa chọn khung đường", Toast.LENGTH_SHORT).show()
            }
        }


    }

    //show all transport
    private fun showDialogTransport() {
        AlertDialog.Builder(this)
            .setTitle("Chọn phương tiện") //.setMessage("Yes or No")
            .setSingleChoiceItems(
                R.array.option_tranports, 0
            ) { dialog, position -> currentTranportId = position }
            .setPositiveButton(
                "Ok"
            ) { dialog, which ->
                when (currentTranportId) {
                    0 -> transportMode = RouteOptions.TransportMode.CAR
                    1 -> transportMode = RouteOptions.TransportMode.PEDESTRIAN
                    2 -> transportMode = RouteOptions.TransportMode.PUBLIC_TRANSPORT
                    3 -> transportMode = RouteOptions.TransportMode.BICYCLE
                    4 -> transportMode = RouteOptions.TransportMode.SCOOTER
                    else -> 0
                }
                calculateRoute(GeoCoordinate(currentPosition!!.latitude,currentPosition!!.longitude),endPointLocation!!)
                isCalculated = true
            }
            .setNegativeButton(
                "cancel"
            ) { dialog, which ->
            }.show()
    }

    //getAddress by position
    fun reverseGeocode(position: GeoCoordinate?) {
        progress_bar.visibility = View.VISIBLE
        val request = ReverseGeocodeRequest(position!!)
        request.execute { p0, p1 ->
            progress_bar.visibility = View.GONE
            if (p1 !== ErrorCode.NONE) {
                Log.e("HERE", p1.toString())
                Toast.makeText(this, "Không xác định được vị trí", Toast.LENGTH_SHORT).show()
            } else {
                var str = String.format(
                    "long: %.2f, lat: %.2f",
                    p0!!.coordinate!!.longitude,
                    p0!!.coordinate!!.latitude
                )
                AlertDialog.Builder(this).setTitle("Thông tin")
                    .setMessage("Địa chỉ: ${p0!!.address.toString()} \n Tọa độ: $str")
                    .setPositiveButton("Ok") { _, _ ->
                    }.setNegativeButton("Chỉ đường") { _, _ ->
                        showDialogTransport()
                    }
                    .show()
            }
        }
    }

    //convert second to minutes
    fun timerConversion(value: Long): String {
        val totalTime: String
        val dur = value.toInt()
        val hrs = dur / 3600
        val mns = dur / 60 % 60
        val scs = dur % 60
        totalTime = if (hrs > 0) {
            String.format("%02d tiếng, %02d phút", hrs, mns, scs)
        } else {
            String.format("%02d phút", mns, scs)
        }
        return totalTime
    }

    //directions from current location to any location
    private fun calculateRoute(startPoint: GeoCoordinate,endPoint:GeoCoordinate) {
        cleanMap()
        mMap!!.setCenter(
            startPoint,
            Map.Animation.NONE
        )
        addMarkerAtPlace(
            startPoint,
            R.drawable.location_marker
        )
        addMarkerAtPlace(endPoint, R.drawable.marker)

        mMap!!.zoomLevel = 12.0

        val routeOptions = RouteOptions()
        val startPosition =
            RouteWaypoint(
                startPoint
            )
        val endPosition =
            RouteWaypoint(endPoint!!)

        //line 1
        val coreRouter1 = CoreRouter()
        val routePlan1 = RoutePlan()
        routeOptions.transportMode = transportMode
        routeOptions.setHighwaysAllowed(false)
        routeOptions.routeType = RouteOptions.Type.BALANCED
        routeOptions.routeCount = 1
        routePlan1.routeOptions = routeOptions
        routePlan1.addWaypoint(startPosition)
        routePlan1.addWaypoint(endPosition)
        coreRouter1.calculateRoute(routePlan1,
            object :
                Router.Listener<List<RouteResult>, RoutingError> {
                override fun onProgress(i: Int) {}

                override fun onCalculateRouteFinished(p0: List<RouteResult>, p1: RoutingError) {
                    if (p1 == RoutingError.NONE) {
//                                        m_map!!.removeMapObject(m_mapRoute!!)
                        if (p0!![0].route != null) {
                            /* Create a MapRoute so that it can be placed on the map */
                            mapRoute = MapRoute(p0[0].route)
                            mapRoute!!.isManeuverNumberVisible = true

                            mMap!!.addMapObject(mapRoute!!)
                            mapObjectList.add(mapRoute!!)
//
//                            val length = mapRoute!!.route!!.length.toString()
//                            val time = p0[0].route.getTtaExcludingTraffic(Route.WHOLE_ROUTE)!!.duration
                            val gbb = p0[0].route.boundingBox
                            mMap!!.zoomTo(
                                gbb!!,
                                Map.Animation.LINEAR,
                                Map.MOVE_PRESERVE_ORIENTATION
                            )
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "Không thể tìm được quãng đường thích hợp",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "Không thể tìm được quãng đường thích hợp",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            })

        //line 2
        val coreRouter = CoreRouter()
        val routePlan = RoutePlan()
        routeOptions.transportMode = transportMode
        routeOptions.setHighwaysAllowed(false)
        routeOptions.routeType = RouteOptions.Type.SHORTEST
        routeOptions.routeCount = 1
        routePlan.routeOptions = routeOptions
        routePlan.addWaypoint(startPosition)
        routePlan.addWaypoint(endPosition)
        coreRouter.calculateRoute(routePlan,
            object :
                Router.Listener<List<RouteResult>, RoutingError> {
                override fun onProgress(i: Int) {}

                override fun onCalculateRouteFinished(p0: List<RouteResult>, p1: RoutingError) {
                    if (p1 == RoutingError.NONE) {
//                                        m_map!!.removeMapObject(m_mapRoute!!)
                        if (p0!![0].route != null) {
                            /* Create a MapRoute so that it can be placed on the map */
                            mapRoute = MapRoute(p0[0].route)
                            mapRoute!!.color = Color.YELLOW
                            mapRoute!!.isManeuverNumberVisible = true

                            mMap!!.addMapObject(mapRoute!!)
                            mapObjectList.add(mapRoute!!)

                            //-------------------------------------
                            val length = (mapRoute!!.route!!.length / 1000).toString()
                            val time =
                                p0[0].route.getTtaExcludingTraffic(Route.WHOLE_ROUTE)!!.duration
                            AlertDialog.Builder(this@MainActivity).setTitle("Thông tin quãng đường")
                                .setMessage(
                                    "Khoảng cách: $length km \nThời gian: ${
                                        timerConversion(time.toLong())
                                    }"
                                ).setPositiveButton("Ok") { _, _ ->

                                }.show()
//                            Toast.makeText(
//                                this@MainActivity,
//                                "Khoảng cách: = $length km, \n Thời gian: ${timerConversion(time.toLong())}",
//                                Toast.LENGTH_SHORT
//                            ).show()
                            val gbb = p0[0].route.boundingBox
                            mMap!!.zoomTo(
                                gbb!!,
                                Map.Animation.LINEAR,
                                Map.MOVE_PRESERVE_ORIENTATION
                            )
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "Không thể tìm được quãng đường thích hợp",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "Không thể tìm được quãng đường thích hợp",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            })
        currentTranportId = 0

    }

    //move to gps position and add marker
    private fun moveToCurrentPosition() {
        if (currentPosition != null) {
            mMap!!.setCenter(
                GeoCoordinate(currentPosition!!.latitude, currentPosition!!.longitude),
                Map.Animation.LINEAR
            )
            addMarkerAtPlace(
                GeoCoordinate(currentPosition!!.latitude, currentPosition!!.longitude),
                R.drawable.location_marker
            )
        } else {
            Toast.makeText(this, "Chưa bật GPS, không thể xác định vị trí", Toast.LENGTH_SHORT)
                .show()
        }
    }

    //get position by gps
    private fun getCurrentPosisition() {
        try {
            val locationManager =
                mActivity!!.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (ActivityCompat.checkSelfPermission(
                    mActivity!!,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    mActivity!!,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 0, 0f,
                (this as LocationListener)
            )
            currentPosition = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
//            Log.d("locationn", "getCurrentPosisition: $currentPosition")
        } catch (ex: Exception) {
            currentPosition = null
            Toast.makeText(this, "Vui lòng bật GPS để thực hiện ", Toast.LENGTH_SHORT).show()
        }

    }

    //add marker by position
    private fun addMarkerAtPlace(geoCoordinate: GeoCoordinate, resource: Int) {
        val img = Image()
        try {
            img.setImageResource(resource)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        val mapMarker = MapMarker()
        mapMarker.icon = img
        mapMarker.coordinate = geoCoordinate
        mMap!!.addMapObject(mapMarker)
        mapObjectList.add(mapMarker)
    }

    //clean all object in map
    private fun cleanMap() {
        if (!mapObjectList.isEmpty()) {
            mMap!!.removeMapObjects(mapObjectList)
            mapObjectList.clear()
        }
    }

    // search view listener
    private inner class SearchListener : SearchView.OnQueryTextListener {
        override fun onQueryTextSubmit(query: String): Boolean {
            return false
        }

        override fun onQueryTextChange(newText: String): Boolean {
            if (!newText.isEmpty()) {
//                Log.d("doSearch", "onQueryTextChange: $newText")
                doSearch(newText)
            } else {
                setSearchMode(false)
            }
            return false
        }
    }

    //request place by suggest in recycler view
    private fun handleSelectedAutoSuggest(autoSuggest: AutoSuggest) {
        //    int collectionSize = Integer.parseInt(m_collectionSizeTextView.getText().toString());
        when (autoSuggest.type) {
            AutoSuggest.Type.PLACE -> {
                val autoSuggestPlace = autoSuggest as AutoSuggestPlace
                val detailsRequest = autoSuggestPlace.placeDetailsRequest
                detailsRequest!!.execute(object : ResultListener<Place> {
                    override fun onCompleted(p0: Place?, p1: ErrorCode?) {
                        if (p1 == ErrorCode.NONE) {
                            handleChoosePlace(p0!!)
                        } else {
                            handleError(p1!!)
                        }
                    }
                })
            }
            AutoSuggest.Type.SEARCH -> {
                val autoSuggestSearch = autoSuggest as AutoSuggestSearch
                val discoverRequest = autoSuggestSearch.suggestedSearchRequest
                // discoverRequest.setCollectionSize(collectionSize);
                discoverRequest!!.execute(object : ResultListener<DiscoveryResultPage> {
                    override fun onCompleted(p0: DiscoveryResultPage?, p1: ErrorCode?) {
                        if (p1 == ErrorCode.NONE) {
                            var result = p0!!.items[0]
                            if (result.resultType == DiscoveryResult.ResultType.PLACE) {
                                /* Fire the PlaceRequest */
                                val placeLink = result as PlaceLink
                                val placeRequest = placeLink.detailsRequest
                                placeRequest!!.execute(placeResultListener)
                            } else if (result.resultType == DiscoveryResult.ResultType.DISCOVERY) {

                                Toast.makeText(
                                    this@MainActivity,
                                    "This is a DiscoveryLink result",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            handleError(p1!!)
                        }
                    }
                })
            }
            AutoSuggest.Type.UNKNOWN -> {
            }
            else -> {
            }
        }
    }

    //add marker to place choose
    fun handleChoosePlace(place: Place) {
        setSearchMode(false)
        cleanMap()
        mMap!!.setCenter(GeoCoordinate(place.location!!.coordinate!!), Map.Animation.NONE)
        addMarkerAtPlace(GeoCoordinate(place.location!!.coordinate!!), R.drawable.marker)
        addMarkerAtPlace(
            GeoCoordinate(currentPosition!!.longitude, currentPosition!!.latitude),
            R.drawable.location_marker
        )
        endPointLocation = GeoCoordinate(place.location!!.coordinate!!)
    }


    private fun handleError(errorCode: ErrorCode) {
        showMessage("Error", "Error description: " + errorCode.name, true)
    }

    private fun showMessage(title: String, message: String, isError: Boolean) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title).setMessage(message)
        if (isError) {
            builder.setIcon(android.R.drawable.ic_dialog_alert)
        } else {
            builder.setIcon(android.R.drawable.ic_dialog_info)
        }
        builder.setNeutralButton("OK", null)
        builder.create().show()
        setSearchMode(false)
    }

    //place listener
    private val placeResultListener: ResultListener<Place> = object : ResultListener<Place> {

        override fun onCompleted(place: Place?, errorCode: ErrorCode?) {
            if (errorCode == ErrorCode.NONE) {
                handleChoosePlace(place!!)
            } else {
                Toast.makeText(
                    applicationContext,
                    "Lỗi khi xác định vị trí", Toast.LENGTH_SHORT
                )
                    .show()
            }
        }
    }

    //search by query
    private fun doSearch(query: String) {
        setSearchMode(true)
        val textAutoSuggestionRequest = TextAutoSuggestionRequest(query)
        textAutoSuggestionRequest.setSearchCenter(mMap!!.center)

        textAutoSuggestionRequest.execute(object : ResultListener<List<AutoSuggest>> {

            override fun onCompleted(p0: List<AutoSuggest>?, p1: ErrorCode?) {
                if (p1 == ErrorCode.NONE) {
//                    Log.d("doSearch", "onCompleted: $p0")
                    processSearchResults(p0!!)
                } else {
                    handleError(p1!!)
                }
            }
        })
    }

    //set adapter recycler view
    private fun processSearchResults(autoSuggests: List<AutoSuggest>) {
        listAutoSuggest.clear()
        listAutoSuggest.addAll(autoSuggests)
        autoSuggestAdapter.notifyDataSetChanged()
    }

    //set when searching is true
    fun setSearchMode(isSearch: Boolean) {
        if (isSearch) {
            mapFragmentView.visibility = View.GONE
            rcv_resutl!!.visibility = View.VISIBLE
        } else {
            mapFragmentView.visibility = View.VISIBLE
            rcv_resutl!!.visibility = View.GONE
            val imm =
                getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(search_view.windowToken, 0)
        }
    }

    //check all permission
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

    //on gesture listener
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
            Toast.makeText(
                this,
                "Initialization of venue service is in progress...",
                Toast.LENGTH_SHORT
            ).show()
            return false
        }
        val touchLocation: GeoCoordinate = mMap!!.pixelToGeo(p)!!
        mMap!!.setCenter(
            touchLocation,
            Map.Animation.LINEAR
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
            Toast.makeText(
                this,
                "Đang tải bản đồ...",
                Toast.LENGTH_SHORT
            ).show()
            return false
        } else {
            cleanMap()
            val touchLocation: GeoCoordinate = mMap!!.pixelToGeo(p)!!
            reverseGeocode(touchLocation)
            addMarkerAtPlace(touchLocation, R.drawable.marker)
            addMarkerAtPlace(
                GeoCoordinate(currentPosition!!.latitude, currentPosition!!.longitude),
                R.drawable.location_marker
            )
            endPointLocation = touchLocation
            return false
        }

    }


    override fun onTwoFingerTapEvent(p0: PointF): Boolean {
        return false
    }

    //Location listerner
    override fun onLocationChanged(location: Location) {
        currentPosition = location
    }

    override fun onProviderDisabled(provider: String) {
        Toast.makeText(this, "Vui lòng bật GPS để sử dụng ứng dụng", Toast.LENGTH_SHORT).show()
    }

    override fun onProviderEnabled(provider: String) {
        Toast.makeText(this, "Đã kích hoạt GPS", Toast.LENGTH_SHORT).show()
        getCurrentPosisition()
    }


//    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
//        super.onStatusChanged(provider, status, extras)
//    }

}