package com.ducdiep.map.viewmodels

import android.app.Application
import android.content.Context
import android.graphics.Color
import android.graphics.PointF
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.ducdiep.map.R
import com.here.android.mpa.common.GeoCoordinate
import com.here.android.mpa.common.Image
import com.here.android.mpa.mapping.*
import com.here.android.mpa.mapping.Map
import com.here.android.mpa.routing.*
import com.here.android.mpa.search.*
import com.here.android.mpa.search.Location
import java.io.IOException

class MapViewModel(application: Application) : AndroidViewModel(application) {
    var context: Context = getApplication<Application>().applicationContext

    var mMap: Map? = null
    var isLoading = MutableLiveData<Boolean>()
    var isSearching = MutableLiveData<Boolean>()
    var isRequestFailed = MutableLiveData<Boolean>()
    var isDirectFailed = MutableLiveData<Boolean>()
    var touchLocation = MutableLiveData<Location>()
    var listAutoSuggest = MutableLiveData<List<AutoSuggest>?>()
    var directInfor = MutableLiveData<String>()
    var currentPosition: GeoCoordinate? = null
    var endPointLocation: GeoCoordinate? = null
    var mapObjectList: ArrayList<MapObject> = ArrayList()
    var mapRoute: MapRoute? = null
    var currentTransportId = 0
    lateinit var transportMode: RouteOptions.TransportMode

    fun initAttributeMap() {
//        mMap = mapFragment.map
        if (currentPosition != null) {
            moveToCurrentPosition()
        } else {
            mMap?.setCenter(GeoCoordinate(21.05401, 105.73507), Map.Animation.NONE)
            addMarkerAtPlace(
                GeoCoordinate(21.05401, 105.73507), R.drawable.location_marker
            )
        }
        mMap?.zoomLevel = 12.0
    }

    fun onTap(p: PointF) {
        val touchPosition: GeoCoordinate? = mMap?.pixelToGeo(p)
        if (touchPosition != null) {
            mMap?.setCenter(touchPosition, Map.Animation.LINEAR)
        }
    }

    fun onLongClick(p: PointF) {
        cleanMap()
        val touchPosition: GeoCoordinate? = mMap?.pixelToGeo(p)
        if (touchPosition != null) {
            reverseGeocode(touchPosition)
            addMarkerAtPlace(touchPosition, R.drawable.marker)
            endPointLocation = touchPosition
        }
        addMarkerAtPlace(currentPosition!!, R.drawable.location_marker)
        return

    }

    //move to gps position and add marker
    fun moveToCurrentPosition() {
        if (currentPosition != null) {
            mMap?.setCenter(currentPosition!!, Map.Animation.LINEAR)
            addMarkerAtPlace(currentPosition!!, R.drawable.location_marker)
        } else {
            isRequestFailed.value = true
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
        mMap?.addMapObject(mapMarker)
        mapObjectList.add(mapMarker)
    }

    //get address by position
    fun reverseGeocode(position: GeoCoordinate) {
        isLoading.value = true
        val request = ReverseGeocodeRequest(position)
        request.execute { p0, p1 ->
            isLoading.value = false
            if (p1 !== ErrorCode.NONE) {
                isRequestFailed.value = true
            } else {
                touchLocation.value = p0
            }
        }
    }

    //directions from current location to any location
    fun calculateRoute(startPoint: GeoCoordinate, endPoint: GeoCoordinate) {
        cleanMap()
        mMap?.setCenter(startPoint, Map.Animation.NONE)
        addMarkerAtPlace(startPoint, R.drawable.location_marker)
        addMarkerAtPlace(endPoint, R.drawable.marker)
        mMap?.zoomLevel = 12.0
        val routeOptions = RouteOptions()
        val startPosition = RouteWaypoint(startPoint)
        val endPosition = RouteWaypoint(endPoint)

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
        coreRouter1.calculateRoute(
            routePlan1,
            object : Router.Listener<List<RouteResult>, RoutingError> {
                override fun onProgress(i: Int) {}
                override fun onCalculateRouteFinished(p: List<RouteResult>, p1: RoutingError) {
                    if (p1 == RoutingError.NONE) {
                        drawLine(p[0].route,Color.BLUE)
                    } else {
                        isDirectFailed.value = true
                    }
                }
            })

        //line 2
        val coreRouter2 = CoreRouter()
        val routePlan2 = RoutePlan()
        routeOptions.routeType = RouteOptions.Type.SHORTEST
        routePlan2.routeOptions = routeOptions
        routePlan2.addWaypoint(startPosition)
        routePlan2.addWaypoint(endPosition)
        coreRouter2.calculateRoute(
            routePlan2,
            object : Router.Listener<List<RouteResult>, RoutingError> {
                override fun onProgress(i: Int) {}
                override fun onCalculateRouteFinished(p: List<RouteResult>, p1: RoutingError) {
                    if (p1 == RoutingError.NONE) {
                        drawLine(p[0].route,Color.YELLOW)
                        val length = (mapRoute?.route?.length!! / 1000).toString()
                        val time = p[0].route.getTtaExcludingTraffic(Route.WHOLE_ROUTE)?.duration
                        directInfor.value =
                            "Khoảng cách: $length km \nThời gian: ${timerConversion(time!!.toLong())}"
                    } else {
                        isDirectFailed.value = true
                    }
                }
            })
        currentTransportId = 0
    }

    private fun drawLine(router: Route,color: Int) {
        mapRoute = MapRoute(router)
        mapRoute!!.color = color
        mapRoute!!.isManeuverNumberVisible = true

        mMap?.addMapObject(mapRoute!!)
        mapObjectList.add(mapRoute!!)
        val gbb = router.boundingBox
        mMap?.zoomTo(gbb!!, Map.Animation.LINEAR, Map.MOVE_PRESERVE_ORIENTATION)
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
            mMap?.removeMapObjects(mapObjectList)
            mapObjectList.clear()
        }
    }

    //search by query
    fun doSearch(query: String) {
        isSearching.value = true
        val textAutoSuggestionRequest = TextAutoSuggestionRequest(query)
        textAutoSuggestionRequest.setSearchCenter(mMap?.center!!)

        textAutoSuggestionRequest.execute(object : ResultListener<List<AutoSuggest>> {
            override fun onCompleted(p0: List<AutoSuggest>?, p1: ErrorCode?) {
                if (p1 == ErrorCode.NONE) {
                    listAutoSuggest.value = p0
                } else {
                    isRequestFailed.value = false
                }
            }
        })
    }

    //add marker to place choose
    fun handleChoosePlace(place: Place?) {
        isSearching.value = false
        cleanMap()
        mMap?.setCenter(GeoCoordinate(place?.location?.coordinate!!), Map.Animation.NONE)
        addMarkerAtPlace(GeoCoordinate(place?.location?.coordinate!!), R.drawable.marker)
        addMarkerAtPlace(
            currentPosition!!,
            R.drawable.location_marker
        )
        endPointLocation = GeoCoordinate(place?.location?.coordinate!!)
    }

    //request place by suggest in recycler view
    fun handleSelectedAutoSuggest(autoSuggest: AutoSuggest) {
        when (autoSuggest.type) {
            AutoSuggest.Type.PLACE -> {
                requestTypePlace(autoSuggest)
            }
            AutoSuggest.Type.SEARCH -> {
                requestSearchPlace(autoSuggest)
            }
            AutoSuggest.Type.UNKNOWN -> {
            }
            else -> {
            }
        }
    }

    private fun requestSearchPlace(autoSuggest: AutoSuggest) {
        val autoSuggestSearch = autoSuggest as AutoSuggestSearch
        val discoverRequest = autoSuggestSearch.suggestedSearchRequest
        discoverRequest?.execute(object : ResultListener<DiscoveryResultPage> {
            override fun onCompleted(p0: DiscoveryResultPage?, p1: ErrorCode?) {
                if (p1 == ErrorCode.NONE) {
                    var result = p0?.items?.get(0)
                    if (result?.resultType == DiscoveryResult.ResultType.PLACE) {
                        val placeLink = result as PlaceLink
                        val placeRequest = placeLink.detailsRequest
                        placeRequest?.execute(placeResultListener)
                    } else if (result?.resultType == DiscoveryResult.ResultType.DISCOVERY) {
                        isRequestFailed.value = false
                    }
                } else {
                    isRequestFailed.value = false

                }
            }
        })
    }

    private fun requestTypePlace(autoSuggest: AutoSuggest) {
        val autoSuggestPlace = autoSuggest as AutoSuggestPlace
        val detailsRequest = autoSuggestPlace.placeDetailsRequest
        detailsRequest?.execute(object : ResultListener<Place> {
            override fun onCompleted(p0: Place?, p1: ErrorCode?) {
                if (p1 == ErrorCode.NONE) {
                    handleChoosePlace(p0)
                } else {
                    isRequestFailed.value = false
                }
            }
        })
    }

    //place listener
    private val placeResultListener: ResultListener<Place> = object : ResultListener<Place> {

        override fun onCompleted(place: Place?, errorCode: ErrorCode?) {
            if (errorCode == ErrorCode.NONE) {
                handleChoosePlace(place)
            } else {
                isRequestFailed.value = false
            }
        }
    }

}