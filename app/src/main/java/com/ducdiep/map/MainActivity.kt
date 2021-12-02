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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.observe
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import com.ducdiep.map.adapters.AutoSuggestAdapter
import com.ducdiep.map.viewmodels.MapViewModel
import com.here.android.mpa.common.*
import com.here.android.mpa.mapping.*
import com.here.android.mpa.mapping.Map
import com.here.android.mpa.routing.*
import com.here.android.mpa.search.*
import kotlinx.android.synthetic.main.activity_main.*

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


class MainActivity : AppCompatActivity(), LocationListener {

    lateinit var mapViewModel: MapViewModel
    private var mapFragment: AndroidXMapFragment? = null
    private lateinit var mapFragmentView: View
    private var listAutoSuggest: MutableList<AutoSuggest> = ArrayList()
    private lateinit var autoSuggestAdapter: AutoSuggestAdapter
    private lateinit var searchListener: SearchListener
    var isCalculated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initViewModel()
        initView()
        setClick()
        supportActionBar?.hide()
        checkPermissions()
    }

    private fun initViewModel() {
        mapViewModel = ViewModelProvider(this,ViewModelProvider.AndroidViewModelFactory.getInstance(this.application)).get(MapViewModel::class.java)
        mapViewModel.touchLocation.observe(this) {
            showInforTouchLocation(it)
        }
        mapViewModel.isSearching.observe(this) {
            setSearchMode(it)
        }
        mapViewModel.listAutoSuggest.observe(this) {
            processSearchResults(it)
        }
        mapViewModel.isLoading.observe(this){
            if (it==true){
                progress_bar.visibility = View.VISIBLE
            }else{
                progress_bar.visibility = View.GONE
            }
        }
        mapViewModel.directInfor.observe(this){
            showDirectInfor(it)
        }
    }

    private fun showDirectInfor(it: String?) {
        AlertDialog.Builder(this).setTitle("Thông tin quãng đường ngắn nhất")
            .setMessage(
                it
            ).setPositiveButton("Ok") { _, _ ->
            }.show()
    }

    fun showInforTouchLocation(it: com.here.android.mpa.search.Location) {
        var str = String.format(
            "long: %.2f, lat: %.2f",
            it!!.coordinate!!.longitude,
            it!!.coordinate!!.latitude
        )
        AlertDialog.Builder(this).setTitle("Thông tin")
            .setMessage("Địa chỉ: ${it!!.address.toString()} \n Tọa độ: $str")
            .setPositiveButton("Ok") { _, _ ->
            }.setNegativeButton("Chỉ đường") { _, _ ->
                showDialogTransport()
            }
            .show()
    }

    private fun initView() {
        mapFragmentView = findViewById(R.id.map_fragment)
        mapFragment =
            supportFragmentManager.findFragmentById(R.id.map_fragment) as AndroidXMapFragment?
        mapViewModel.initMap(mapFragment!!)
        getCurrentPosisition()
    }

    fun setClick() {
        //set up search view
        searchListener = SearchListener()
        search_view.setOnQueryTextListener(searchListener)

        //setup adapter search
        autoSuggestAdapter = AutoSuggestAdapter(this, listAutoSuggest)
        autoSuggestAdapter.setOnClickItem {
            mapViewModel.handleSelectedAutoSuggest(it)
        }

        val dividerItemDecoration = DividerItemDecoration(
            this,
            RecyclerView.VERTICAL
        )
        rcv_resutl.adapter = autoSuggestAdapter
        rcv_resutl.addItemDecoration(dividerItemDecoration)

        //set on click button
        btn_gps.setOnClickListener {
            mapViewModel.moveToCurrentPosition()
        }

        btn_direct.setOnClickListener {
            if (mapViewModel.endPointLocation !== null) {
                showDialogTransport()
            } else {
                Toast.makeText(this, "Chưa chọn điểm đến", Toast.LENGTH_SHORT).show()
            }
        }

        btn_swap.setOnClickListener {
            if (isCalculated) {
                mapViewModel.calculateRoute(
                    mapViewModel.endPointLocation!!,
                    mapViewModel.currentPosition!!
                )
            } else {
                Toast.makeText(this, "Chưa chọn khung đường", Toast.LENGTH_SHORT).show()
            }
        }
    }

    //show all transport
    private fun showDialogTransport() {
        AlertDialog.Builder(this)
            .setTitle("Chọn phương tiện")
            .setSingleChoiceItems(
                R.array.option_tranports, 0
            ) { dialog, position -> mapViewModel.currentTranportId = position }
            .setPositiveButton(
                "Ok"
            ) { dialog, which ->
                when (mapViewModel.currentTranportId) {
                    0 -> mapViewModel.transportMode = RouteOptions.TransportMode.CAR
                    1 -> mapViewModel.transportMode = RouteOptions.TransportMode.PEDESTRIAN
                    2 -> mapViewModel.transportMode = RouteOptions.TransportMode.PUBLIC_TRANSPORT
                    3 -> mapViewModel.transportMode = RouteOptions.TransportMode.BICYCLE
                    4 -> mapViewModel.transportMode = RouteOptions.TransportMode.SCOOTER
                    else -> 0
                }
                mapViewModel.calculateRoute(
                    mapViewModel.currentPosition!!, mapViewModel.endPointLocation!!
                )
                isCalculated = true
            }
            .setNegativeButton(
                "cancel"
            ) { dialog, which ->
            }.show()
    }

    //get position by gps
    private fun getCurrentPosisition() {
        try {
            val locationManager =
                getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 0, 0f,
                (this as LocationListener)
            )
            var position = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            mapViewModel.currentPosition = GeoCoordinate(position!!.latitude, position.longitude)
        } catch (ex: Exception) {
            mapViewModel.currentPosition = null
            Toast.makeText(this, "Vui lòng bật GPS để thực hiện thao tác ${ex.message}", Toast.LENGTH_SHORT).show()
        }

    }

    // search view listener
    private inner class SearchListener : SearchView.OnQueryTextListener {
        override fun onQueryTextSubmit(query: String): Boolean {
            return false
        }

        override fun onQueryTextChange(newText: String): Boolean {
            if (!newText.isEmpty()) {
                mapViewModel.doSearch(newText)
            } else {
                setSearchMode(false)
            }
            return false
        }
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
                mapViewModel.initMap(mapFragment!!)
            }
        }
    }

    //Location listerner
    override fun onLocationChanged(location: Location) {
        mapViewModel.currentPosition = GeoCoordinate(location!!.latitude, location.longitude)
    }

    override fun onProviderDisabled(provider: String) {
        Toast.makeText(this, "Vui lòng bật GPS để sử dụng ứng dụng", Toast.LENGTH_SHORT).show()
    }

    override fun onProviderEnabled(provider: String) {
        Toast.makeText(this, "Đã kích hoạt GPS", Toast.LENGTH_SHORT).show()
        getCurrentPosisition()
    }

}