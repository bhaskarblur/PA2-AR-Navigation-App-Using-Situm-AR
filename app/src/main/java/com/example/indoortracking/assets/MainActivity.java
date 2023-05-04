package com.example.indoortracking.assets;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.widget.Toast;

import com.example.indoortracking.R;
import com.example.indoortracking.buildingAdapter;
import com.example.indoortracking.databinding.ActivityMainBinding;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import es.situm.sdk.SitumSdk;
import es.situm.sdk.error.Error;
import es.situm.sdk.model.cartography.Building;
import es.situm.sdk.model.cartography.BuildingInfo;
import es.situm.sdk.utils.Handler;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private  buildingAdapter adapter;
    private List<Building> buildingsList;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding= ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        SitumSdk.init(MainActivity.this);
        ManageUI();
    }

    private void ManageUI() {
        fillData();
        adapter=new buildingAdapter(MainActivity.this, buildingsList);
        LinearLayoutManager llm=new LinearLayoutManager(MainActivity.this);
        llm.setOrientation(RecyclerView.VERTICAL);
        binding.buildingList.setLayoutManager(llm);
        binding.buildingList.setAdapter(adapter);
    }

    private void fillData() {
        buildingsList=new ArrayList<>();
        SitumSdk.communicationManager().fetchBuildings(new Handler<Collection<Building>>() {
            @Override
            public void onSuccess(Collection<Building> buildings) {
                for(Building building: buildings) {
                    buildingsList.add(building);
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onFailure(Error error) {
                Toast.makeText(MainActivity.this, "Error finding buildings.", Toast.LENGTH_SHORT).show();
            }
        });
    }
}