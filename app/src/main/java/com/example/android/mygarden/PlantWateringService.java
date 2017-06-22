package com.example.android.mygarden;

import android.app.IntentService;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;

import com.example.android.mygarden.provider.PlantContract;
import com.example.android.mygarden.utils.PlantUtils;

import static com.example.android.mygarden.provider.PlantContract.BASE_CONTENT_URI;
import static com.example.android.mygarden.provider.PlantContract.INVALID_PLANT_ID;
import static com.example.android.mygarden.provider.PlantContract.PATH_PLANTS;

/**
 * Created by mhigh on 6/22/2017.
 */

public class PlantWateringService extends IntentService {

    public static final String ACTION_WATER_PLANT = "com.example.android.mygarden.action.water_plant";
    public static final String ACTION_UPDATE_PLANTS_WIDGETS = "com.example.android.mygarden.action.update_plant_widgets";
    public static final String EXTRA_PLANT_ID = "com.example.android.mygarden.extra.PLANT_ID";


    public PlantWateringService(){
        super("PlantWateringService");
    }

    public static void startActionWaterPlant(Context context, long plantId){
        Intent intent = new Intent(context, PlantWateringService.class);
        intent.setAction(ACTION_WATER_PLANT);
        intent.putExtra(EXTRA_PLANT_ID, plantId);
        context.startService(intent);
    }

    public static void startActionUpdatePlantWidgets(Context context){
        Intent intent = new Intent(context, PlantWateringService.class);
        intent.setAction(ACTION_UPDATE_PLANTS_WIDGETS);
        context.startService(intent);
    }

    //Handle the action types
    @Override
    protected void onHandleIntent(Intent intent){
        if(intent != null){
            final String action = intent.getAction();
            if(ACTION_WATER_PLANT.equals(action)){
                final long plantId = intent.getLongExtra(EXTRA_PLANT_ID, PlantContract.INVALID_PLANT_ID);
                handleActionWaterPlant(plantId);
            } else if (ACTION_UPDATE_PLANTS_WIDGETS.equals(action)){
                handleActionUpdatePlantWidgets();
            }
        }
    }

    private void handleActionWaterPlant(long plantId){
        Uri SINGLE_PLANT_URI = ContentUris.withAppendedId(BASE_CONTENT_URI.buildUpon().appendPath(PATH_PLANTS).build(), plantId);
        ContentValues contentValues = new ContentValues();
        long timeNow = System.currentTimeMillis();
        contentValues.put(PlantContract.PlantEntry.COLUMN_LAST_WATERED_TIME, timeNow);

        //Update only if that plant is still alive
        getContentResolver().update(
                SINGLE_PLANT_URI,
                contentValues,
                PlantContract.PlantEntry.COLUMN_LAST_WATERED_TIME+">?",
                new String []{String.valueOf(timeNow - PlantUtils.MAX_AGE_WITHOUT_WATER)});
        //update the widget after watering plants
        startActionUpdatePlantWidgets(this);
    }

    private void handleActionUpdatePlantWidgets(){
        //Query to get the plant that's closest to dying (most in need of water)
        Uri PLANT_URI = BASE_CONTENT_URI.buildUpon().appendPath(PATH_PLANTS).build();
        Cursor cursor = getContentResolver().query(
                PLANT_URI,
                null,
                null,
                null,
                PlantContract.PlantEntry.COLUMN_LAST_WATERED_TIME
        );

        //Extract the plant details
        int imgRes = R.drawable.grass; //Default image when the garden is empty
        boolean canWater = false; //Default to hide the water drop button
        long plantId = INVALID_PLANT_ID;
        if(cursor != null && cursor.getCount() > 0){
            cursor.moveToFirst();
            int idIndex = cursor.getColumnIndex(PlantContract.PlantEntry._ID);
            int createTimeIndex = cursor.getColumnIndex(PlantContract.PlantEntry.COLUMN_CREATION_TIME);
            int waterTimeIndex = cursor.getColumnIndex(PlantContract.PlantEntry.COLUMN_LAST_WATERED_TIME);
            int plantTypeIndex = cursor.getColumnIndex(PlantContract.PlantEntry.COLUMN_PLANT_TYPE);
            plantId = cursor.getLong(idIndex);
            long timeNow = System.currentTimeMillis();
            long wateredAt = cursor.getLong(waterTimeIndex);
            long createdAt = cursor.getLong(waterTimeIndex);
            int plantType = cursor.getInt(plantTypeIndex);
            cursor.close();
            canWater = (timeNow -wateredAt) > PlantUtils.MIN_AGE_BETWEEN_WATER && (timeNow - wateredAt) < PlantUtils.MAX_AGE_WITHOUT_WATER;
            imgRes = PlantUtils.getPlantImageRes(this, timeNow - createdAt, timeNow - wateredAt, plantType);
        }
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(this, PlantWidgetProvider.class));
        //Now update all widgets
        PlantWidgetProvider.updatePlantWidgets(this, appWidgetManager, imgRes, plantId, canWater, appWidgetIds);
    }

}

