package com.ducdiep.map.viewmodels

import android.app.Application
import android.content.Context
import android.graphics.Color
import android.graphics.PointF
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.ducdiep.map.R
import com.here.android.mpa.common.GeoCoordinate
import com.here.android.mpa.common.Image
import com.here.android.mpa.common.MapSettings
import com.here.android.mpa.common.OnEngineInitListener
import com.here.android.mpa.mapping.*
import com.here.android.mpa.mapping.Map
import com.here.android.mpa.routing.*
import com.here.android.mpa.search.*
import com.here.android.mpa.search.Location
import java.io.File
import java.io.IOException

class MapViewModel(application: Application) : AndroidViewModel(application) {
    var context: Context = getApplication<Application>().applicationContext

    private var mMap: Map? = null
    var isLoading = MutableLiveData<Boolean>()
    var isSearching = MutableLiveData<Boolean>()
    var touchLocation = MutableLiveData<Location>()
    var listAutoSuggest = MutableLiveData<List<AutoSuggest>>()
    var directInfor = MutableLiveData<String>()

    var currentPosition: GeoCoordinate? = null
    var endPointLocation: GeoCoordinate? = null
    var mapObjectList: ArrayList<MapObject> = ArrayList()

    var mapRoute: MapRoute? = null
    var currentTranportId = 0
    lateinit var transportMode: RouteOptions.TransportMode

    //init all
    fun initMap(mapFragment: AndroidXMapFragment) {
        if (mapFragment != null) {
            //set disk cache
            MapSettings.setDiskCacheRootPath(
                "${context.getExternalFilesDir(null)}${File.separator}.here-maps"
            )
            Log.d("Locationn", "initialize: $currentPosition")

            // init mapframent
            mapFragment?.init {
                if (it == OnEngineInitListener.Error.NONE) {//no error
                    mapFragment!!.mapGesture!!.addOnGestureListener(object :
                        MapGesture.OnGestureListener.OnGestureListenerAdapter() {
                        override fun onTapEvent(p: PointF): Boolean {
                            if (mMap == null) {
                                Toast.makeText(
                                    context,
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

                        override fun onLongPressEvent(p: PointF): Boolean {
                            if (mMap == null) {
                                Toast.makeText(
                                    context,
                                    "Đang tải bản đồ...",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return false
                            } else {
                                cleanMap()
                                val touchPosition: GeoCoordinate = mMap!!.pixelToGeo(p)!!
                                reverseGeocode(touchPosition)
                                addMarkerAtPlace(touchPosition, R.drawable.marker)
                                addMarkerAtPlace(
                                    currentPosition!!,
                                    R.drawable.location_marker
                                )
                                endPointLocation = touchPosition
                                return false
                            }

                        }

                    }, 0, false)
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
                    Toast.makeText(context, "Có lỗi khi load bản đồ", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    //move to gps position and add marker
    fun moveToCurrentPosition() {
        if (currentPosition != null) {
            mMap!!.setCenter(
                currentPosition!!,
                Map.Animation.LINEAR
            )
            addMarkerAtPlace(
                currentPosition!!,
                R.drawable.location_marker
            )
        } else {
            Toast.makeText(context, "Chưa bật GPS, không thể xác định vị trí", Toast.LENGTH_SHORT)
                .show()
        }
    }

    //add marker by position
    fun addMarkerAtPlace(geoCoordinate: GeoCoordinate, resource: Int) {
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

    //getAddress by position
    fun reverseGeocode(position: GeoCoordinate?) {
        isLoading.value = true
//        progress_bar.visibility = View.VISIBLE
        val request = ReverseGeocodeRequest(position!!)
        request.execute { p0, p1 ->
//            progress_bar.visibility = View.GONE
            isLoading.value = false
            if (p1 !== ErrorCode.NONE) {
                Log.e("HERE", p1.toString())
                Toast.makeText(context, "Không xác định được vị trí", Toast.LENGTH_SHORT).show()
            } else {
                touchLocation.value = p0
            }
        }
    }

    //directions from current location to any location
    fun calculateRoute(startPoint: GeoCoordinate, endPoint: GeoCoordinate) {
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
                        if (p0!![0].route != null) {
                            mapRoute = MapRoute(p0[0].route)
                            mapRoute!!.isManeuverNumberVisible = true

                            mMap!!.addMapObject(mapRoute!!)
                            mapObjectList.add(mapRoute!!)
                            val gbb = p0[0].route.boundingBox
                            mMap!!.zoomTo(
                                gbb!!,
                                Map.Animation.LINEAR,
                                Map.MOVE_PRESERVE_ORIENTATION
                            )
                        } else {
                            Toast.makeText(
                                context,
                                "Không thể tìm được quãng đường thích hợp",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        Toast.makeText(
                            context,
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
                        if (p0!![0].route != null) {
                            mapRoute = MapRoute(p0[0].route)
                            mapRoute!!.color = Color.YELLOW
                            mapRoute!!.isManeuverNumberVisible = true

                            mMap!!.addMapObject(mapRoute!!)
                            mapObjectList.add(mapRoute!!)

                            val length = (mapRoute!!.route!!.length / 1000).toString()
                            val time =
                                p0[0].route.getTtaExcludingTraffic(Route.WHOLE_ROUTE)!!.duration
                            directInfor.value = "Khoảng cách: $length km \nThời gian: ${
                                timerConversion(time.toLong())
                            }"
                            val gbb = p0[0].route.boundingBox
                            mMap!!.zoomTo(
                                gbb!!,
                                Map.Animation.LINEAR,
                                Map.MOVE_PRESERVE_ORIENTATION
                            )
                        } else {
                            Toast.makeText(
                                context,
                                "Không thể tìm được quãng đường thích hợp",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        Toast.makeText(
                            context,
                            "Không thể tìm được quãng đường thích hợp",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            })
        currentTranportId = 0
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

    //clean all object in map
    fun cleanMap() {
        if (!mapObjectList.isEmpty()) {
            mMap!!.removeMapObjects(mapObjectList)
            mapObjectList.clear()
        }
    }

    //search by query
    fun doSearch(query: String) {
        isSearching.value = true
        val textAutoSuggestionRequest = TextAutoSuggestionRequest(query)
        textAutoSuggestionRequest.setSearchCenter(mMap!!.center)

        textAutoSuggestionRequest.execute(object : ResultListener<List<AutoSuggest>> {

            override fun onCompleted(p0: List<AutoSuggest>?, p1: ErrorCode?) {
                if (p1 == ErrorCode.NONE) {
                    listAutoSuggest.value = p0!!
//                    processSearchResults(p0!!)
                } else {
                    handleError(p1!!)
                }
            }
        })
    }

    //add marker to place choose
    fun handleChoosePlace(place: Place) {
        isSearching.value = false
        cleanMap()
        mMap!!.setCenter(GeoCoordinate(place.location!!.coordinate!!), Map.Animation.NONE)
        addMarkerAtPlace(GeoCoordinate(place.location!!.coordinate!!), R.drawable.marker)
        addMarkerAtPlace(
            GeoCoordinate(currentPosition!!.longitude, currentPosition!!.latitude),
            R.drawable.location_marker
        )
        endPointLocation = GeoCoordinate(place.location!!.coordinate!!)
    }


    //request place by suggest in recycler view
    fun handleSelectedAutoSuggest(autoSuggest: AutoSuggest) {
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
                                    context,
                                    "Đây là khu vực tổng quát",
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

    //place listener
    private val placeResultListener: ResultListener<Place> = object : ResultListener<Place> {

        override fun onCompleted(place: Place?, errorCode: ErrorCode?) {
            if (errorCode == ErrorCode.NONE) {
                handleChoosePlace(place!!)
            } else {
                Toast.makeText(
                    context,
                    "Lỗi khi xác định vị trí", Toast.LENGTH_SHORT
                )
                    .show()
            }
        }
    }

    private fun handleError(errorCode: ErrorCode) {
        showMessage("Error", "Error description: " + errorCode.name, true)
    }

    private fun showMessage(title: String, message: String, isError: Boolean) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle(title).setMessage(message)
        if (isError) {
            builder.setIcon(android.R.drawable.ic_dialog_alert)
        } else {
            builder.setIcon(android.R.drawable.ic_dialog_info)
        }
        builder.setNeutralButton("OK", null)
        builder.create().show()
        isSearching.value = false
    }

}