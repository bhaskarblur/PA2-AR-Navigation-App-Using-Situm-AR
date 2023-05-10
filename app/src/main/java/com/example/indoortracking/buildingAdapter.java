package com.example.indoortracking;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import es.situm.sdk.SitumSdk;
import es.situm.sdk.error.Error;
import es.situm.sdk.model.cartography.Building;
import es.situm.sdk.model.cartography.BuildingInfo;
import es.situm.sdk.model.cartography.Floor;
import es.situm.sdk.model.cartography.Poi;
import es.situm.sdk.utils.Handler;

public class buildingAdapter extends RecyclerView.Adapter<buildingAdapter.ViewHolder> {

    private Context context;
    private List<Building> buildingsList;
    public buildingAdapter(Context context,List<Building> buildingsList) {
        this.context=context;
        this.buildingsList=buildingsList;
    }


    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view= LayoutInflater.from(context).inflate(R.layout.building_view, parent,false);
        SitumSdk.init(context);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

        SitumSdk.communicationManager().fetchBuildingInfo(buildingsList.get(position)
                .getIdentifier(), new Handler<BuildingInfo>() {
            @Override
            public void onSuccess(BuildingInfo buildingInfo) {
                for (Floor floor: buildingInfo.getFloors()) {
                    for(Poi poi: buildingInfo.getIndoorPOIs()) {
                        holder.text.setText("Building: " + buildingInfo.getBuilding().getName().toString() +
                                ", Floor: " + floor.getName().toString()+", POI: "+poi.getName());
                    }
                }
            }

            @Override
            public void onFailure(Error error) {

            }
        });

    }

    @Override
    public int getItemCount() {
        return buildingsList.size();
    }

    public class ViewHolder extends  RecyclerView.ViewHolder{
        TextView text;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            text=itemView.findViewById(R.id.buildingName);
        }
    }
}
