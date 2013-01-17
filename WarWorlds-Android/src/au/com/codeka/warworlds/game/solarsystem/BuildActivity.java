package au.com.codeka.warworlds.game.solarsystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.joda.time.Duration;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import au.com.codeka.TimeInHours;
import au.com.codeka.warworlds.R;
import au.com.codeka.warworlds.TabFragmentActivity;
import au.com.codeka.warworlds.model.BuildQueueManager;
import au.com.codeka.warworlds.model.BuildRequest;
import au.com.codeka.warworlds.model.Building;
import au.com.codeka.warworlds.model.BuildingDesign;
import au.com.codeka.warworlds.model.BuildingDesignManager;
import au.com.codeka.warworlds.model.Colony;
import au.com.codeka.warworlds.model.Design;
import au.com.codeka.warworlds.model.DesignManager;
import au.com.codeka.warworlds.model.ShipDesign;
import au.com.codeka.warworlds.model.ShipDesignManager;
import au.com.codeka.warworlds.model.SpriteDrawable;
import au.com.codeka.warworlds.model.Star;
import au.com.codeka.warworlds.model.StarManager;

/**
 * When you click "Build" shows you the list of buildings/ships that are/can be built by your
 * colony.
 */
public class BuildActivity extends TabFragmentActivity implements StarManager.StarFetchedHandler {
    private Context mContext = this;
    private Star mStar;
    private Colony mColony;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getTabManager().addTab(mContext, new TabInfo("Buildings", BuildingsFragment.class, null));
        getTabManager().addTab(mContext, new TabInfo("Ships", ShipsFragment.class, null));
        getTabManager().addTab(mContext, new TabInfo("Queue", QueueFragment.class, null));
    }

    @Override
    public void onResume() {
        Bundle extras = this.getIntent().getExtras();
        String starKey = extras.getString("au.com.codeka.warworlds.StarKey");
        mColony = (Colony) extras.getParcelable("au.com.codeka.warworlds.Colony");

        StarManager.getInstance().requestStar(mContext, starKey, false, this);
        StarManager.getInstance().addStarUpdatedListener(starKey, this);

        super.onResume();
    }

    @Override
    public void onPause() {
        StarManager.getInstance().removeStarUpdatedListener(this);

        super.onPause();
    }

    /**
     * Called when our star is refreshed/updated. We want to reload the current tab.
     */
    @Override
    public void onStarFetched(Star s) {
        mStar = s;
        getTabManager().reloadTab();
    }

    /**
     * Returns the dependencies of the given design a string for display to
     * the user. Dependencies that we don't meet will be coloured red.
     */
    private String getDependenciesList(Design design) {
        String required = "Required: ";
        ArrayList<Design.Dependency> dependencies = design.getDependencies();
        if (dependencies.size() == 0) {
            required += "none";
        } else {
            int n = 0;
            for (Design.Dependency dep : dependencies) {
                if (n > 0) {
                    required += ", ";
                }

                boolean dependencyMet = false;
                for (Building b : mColony.getBuildings()) {
                    if (b.getDesign().getID().equals(dep.getDesignID())) {
                        // TODO: check level
                        dependencyMet = true;
                    }
                }

                Design dependentDesign = BuildingDesignManager.getInstance().getDesign(dep.getDesignID());
                required += "<font color=\""+(dependencyMet ? "green" : "red")+"\">";
                required += dependentDesign.getDisplayName();
                required += "</font>";
            }
        }

        return required;
    }

    public static class BuildingsFragment extends Fragment {
        private BuildingListAdapter mBuildingListAdapter;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View v = inflater.inflate(R.layout.solarsystem_build_buildings_tab, null);

            final Star star = ((BuildActivity) getActivity()).mStar;
            final Colony colony = ((BuildActivity) getActivity()).mColony;

            mBuildingListAdapter = new BuildingListAdapter();
            if (colony != null) {
                mBuildingListAdapter.setColony(star, colony);
            }

            ListView buildingsList = (ListView) v.findViewById(R.id.building_list);
            buildingsList.setAdapter(mBuildingListAdapter);

            buildingsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    Entry entry = (Entry) mBuildingListAdapter.getItem(position);
                    if (entry.design != null) {
                        int buildQueueSize = 0;
                        BuildActivity activity = (BuildActivity) getActivity();
                        for (BuildRequest br : activity.mStar.getBuildRequests()) {
                            if (br.getColonyKey().equals(colony.getKey())) {
                                buildQueueSize ++;
                            }
                        }

                        BuildConfirmDialog dialog = new BuildConfirmDialog();
                        dialog.setup(entry.design, colony, buildQueueSize);
                        dialog.show(getActivity().getSupportFragmentManager(), "");
                    } else if (entry.building != null) {
                        // TODO: upgrade building
                    }
                }
            });

            return v;
        }

        /**
         * This adapter is used to populate a list of buildings in a list view.
         */
        private class BuildingListAdapter extends BaseAdapter {
            private ArrayList<Entry> mEntries;

            private static final int HEADING_TYPE = 0;
            private static final int EXISTING_BUILDING_TYPE = 1;
            private static final int NEW_BUILDING_TYPE = 2;

            public void setColony(Star star, Colony colony) {
                List<Building> buildings = colony.getBuildings();
                if (buildings == null) {
                    buildings = new ArrayList<Building>();
                }

                mEntries = new ArrayList<Entry>();
                for (Building b : buildings) {
                    Entry entry = new Entry();
                    entry.building = b;
                    mEntries.add(entry);
                }

                for (BuildRequest br : star.getBuildRequests()) {
                    if (br.getColonyKey().equals(colony.getKey()) &&
                        br.getBuildKind().equals(BuildRequest.BuildKind.BUILDING)) {
                        Entry entry = new Entry();
                        entry.buildRequest = br;
                        mEntries.add(entry);
                    }
                }

                Collections.sort(mEntries, new Comparator<Entry>() {
                    @Override
                    public int compare(Entry lhs, Entry rhs) {
                        String a = (lhs.building != null ? lhs.building.getDesignName() : lhs.buildRequest.getDesignID());
                        String b = (rhs.building != null ? rhs.building.getDesignName() : rhs.buildRequest.getDesignID());
                        return a.compareTo(b);
                    }
                });

                Entry title = new Entry();
                title.title = "Existing Buildings";
                mEntries.add(0, title);

                title = new Entry();
                title.title = "Available Buildings";
                mEntries.add(title);

                for (Design d : BuildingDesignManager.getInstance().getDesigns().values()) {
                    BuildingDesign bd = (BuildingDesign) d;
                    if (bd.getMaxPerColony() > 0) {
                        int numExisting = 0;
                        for (Entry e : mEntries) {
                            if (e.building != null) {
                                if (e.building.getDesignName().equals(bd.getID())) {
                                    numExisting ++;
                                }
                            } else if (e.buildRequest != null) {
                                if (e.buildRequest.getDesignID().equals(bd.getID())) {
                                    numExisting ++;
                                }
                            }
                        }
                        if (numExisting >= bd.getMaxPerColony()) {
                            continue;
                        }
                    }
                    Entry entry = new Entry();
                    entry.design = bd;
                    mEntries.add(entry);
                }

                notifyDataSetChanged();
            }

            /**
             * We have three types of items, the "headings", the list of existing buildings
             * and the list of building designs.
             */
            @Override
            public int getViewTypeCount() {
                return 3;
            }

            @Override
            public int getItemViewType(int position) {
                if (mEntries == null)
                    return 0;

                if (mEntries.get(position).title != null)
                    return HEADING_TYPE;
                if (mEntries.get(position).design != null)
                    return NEW_BUILDING_TYPE;
                return EXISTING_BUILDING_TYPE;
            }

            @Override
            public boolean isEnabled(int position) {
                if (getItemViewType(position) == HEADING_TYPE) {
                    return false;
                }

                return true;
            }

            @Override
            public int getCount() {
                if (mEntries == null)
                    return 0;

                return mEntries.size();
            }

            @Override
            public Object getItem(int position) {
                if (mEntries == null)
                    return null;

                return mEntries.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = convertView;
                if (view == null) {
                    LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService
                            (Context.LAYOUT_INFLATER_SERVICE);

                    int viewType = getItemViewType(position);
                    if (viewType == HEADING_TYPE) {
                        view = new TextView(getActivity());
                    } else {
                        view = inflater.inflate(R.layout.solarsystem_buildings_design, null);
                    }
                }

                Entry entry = mEntries.get(position);
                if (entry.title != null) {
                    TextView tv = (TextView) view;
                    tv.setText(entry.title);
                } else if (entry.building != null || entry.buildRequest != null) {
                    ImageView icon = (ImageView) view.findViewById(R.id.building_icon);
                    TextView row1 = (TextView) view.findViewById(R.id.building_row1);
                    TextView row2 = (TextView) view.findViewById(R.id.building_row2);
                    TextView row3 = (TextView) view.findViewById(R.id.building_row3);
                    ProgressBar progress = (ProgressBar) view.findViewById(R.id.building_progress);

                    Building building = entry.building;
                    BuildRequest buildRequest = entry.buildRequest;
                    BuildingDesign design = (building != null
                            ? building.getDesign()
                            : BuildingDesignManager.getInstance().getDesign(buildRequest.getDesignID()));

                    icon.setImageDrawable(new SpriteDrawable(design.getSprite()));

                    row1.setText(design.getDisplayName());
                    if (building != null) {
                        row2.setText("Level 1");
                        row3.setVisibility(View.VISIBLE);
                        progress.setVisibility(View.GONE);
                        row3.setText(String.format(Locale.ENGLISH,
                                "Upgrade: %.2f hours",
                                (float) design.getBuildTimeSeconds() / 3600.0f));
                    } else {
                        row2.setText(String.format(Locale.ENGLISH,
                                "Building: %d %%, %s left",
                                 (int) buildRequest.getPercentComplete(),
                                 TimeInHours.format(buildRequest.getRemainingTime())));

                        row3.setVisibility(View.GONE);
                        progress.setVisibility(View.VISIBLE);
                        progress.setProgress((int) buildRequest.getPercentComplete());
                    }
                } else {
                    ImageView icon = (ImageView) view.findViewById(R.id.building_icon);
                    TextView row1 = (TextView) view.findViewById(R.id.building_row1);
                    TextView row2 = (TextView) view.findViewById(R.id.building_row2);
                    TextView row3 = (TextView) view.findViewById(R.id.building_row3);
                    ProgressBar progress = (ProgressBar) view.findViewById(R.id.building_progress);
                    progress.setVisibility(View.GONE);

                    BuildingDesign design = mEntries.get(position).design;

                    icon.setImageDrawable(new SpriteDrawable(design.getSprite()));

                    row1.setText(design.getDisplayName());
                    row2.setText(String.format("%.2f hours",
                            (float) design.getBuildTimeSeconds() / 3600.0f));

                    String required = ((BuildActivity) getActivity()).getDependenciesList(design);
                    row3.setText(required);
                }

                return view;
            }
        }

        private static class Entry {
            public String title;
            public BuildRequest buildRequest;
            public Building building;
            public BuildingDesign design;
        }
    }

    public static class ShipsFragment extends Fragment {
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View v = inflater.inflate(R.layout.solarsystem_build_ships_tab, null);

            final Colony colony = ((BuildActivity) getActivity()).mColony;

            final ShipDesignListAdapter adapter = new ShipDesignListAdapter();
            adapter.setDesigns(ShipDesignManager.getInstance().getDesigns());

            ListView availableDesignsList = (ListView) v.findViewById(R.id.ship_list);
            availableDesignsList.setAdapter(adapter);
            availableDesignsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    int buildQueueSize = 0;
                    BuildActivity activity = (BuildActivity) getActivity();
                    for (BuildRequest br : activity.mStar.getBuildRequests()) {
                        if (br.getColonyKey().equals(colony.getKey())) {
                            buildQueueSize ++;
                        }
                    }
                    ShipDesign design = (ShipDesign) adapter.getItem(position);
                    if (design == null) {
                        // not sure why this would ever happen?
                        return;
                    }

                    BuildConfirmDialog dialog = new BuildConfirmDialog();
                    dialog.setup(design, colony, buildQueueSize);
                    dialog.show(getActivity().getSupportFragmentManager(), "");
                }
            });

            return v;
        }

        /**
         * This adapter is used to populate the list of ship designs in our view.
         */
        private class ShipDesignListAdapter extends BaseAdapter {
            private List<ShipDesign> mDesigns;

            public void setDesigns(Map<String, Design> designs) {
                mDesigns = new ArrayList<ShipDesign>();
                for (Design d : designs.values()) {
                    mDesigns.add((ShipDesign) d);
                }
                notifyDataSetChanged();
            }

            @Override
            public int getCount() {
                if (mDesigns == null)
                    return 0;
                return mDesigns.size();
            }

            @Override
            public Object getItem(int position) {
                if (mDesigns == null)
                    return null;
                return mDesigns.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = convertView;
                if (view == null) {
                    LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService
                            (Context.LAYOUT_INFLATER_SERVICE);
                    view = inflater.inflate(R.layout.solarsystem_buildings_design, null);
                }

                ImageView icon = (ImageView) view.findViewById(R.id.building_icon);
                TextView row1 = (TextView) view.findViewById(R.id.building_row1);
                TextView row2 = (TextView) view.findViewById(R.id.building_row2);
                TextView row3 = (TextView) view.findViewById(R.id.building_row3);
                ProgressBar progress = (ProgressBar) view.findViewById(R.id.building_progress);
                progress.setVisibility(View.GONE);

                ShipDesign design = mDesigns.get(position);

                icon.setImageDrawable(new SpriteDrawable(design.getSprite()));

                row1.setText(design.getDisplayName());
                row2.setText(String.format("%.2f hours",
                        (float) design.getBuildTimeSeconds() / 3600.0f));

                String required = ((BuildActivity) getActivity()).getDependenciesList(design);
                row3.setText(Html.fromHtml(required));

                return view;
            }
        }
    }

    public static class QueueFragment extends Fragment {
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View v = inflater.inflate(R.layout.solarsystem_build_queue_tab, null);

            final Star star = ((BuildActivity) getActivity()).mStar;
            final Colony colony = ((BuildActivity) getActivity()).mColony;
            if (star == null)
                return inflater.inflate(R.layout.solarsystem_build_loading_tab, null);

            final BuildQueueListAdapter adapter = new BuildQueueListAdapter();
            adapter.setBuildQueue(star, colony);

            ListView buildQueueList = (ListView) v.findViewById(R.id.build_queue);
            buildQueueList.setAdapter(adapter);

            buildQueueList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    BuildRequest buildRequest = (BuildRequest) adapter.getItem(position);
                    BuildProgressDialog dialog = new BuildProgressDialog();
                    dialog.setBuildRequest(buildRequest, star.getKey());
                    dialog.show(getActivity().getSupportFragmentManager(), "");
                }
            });

            // make sure we're aware of changes to the build queue
            BuildQueueManager.getInstance().addBuildQueueUpdatedListener(new BuildQueueManager.BuildQueueUpdatedListener() {
                @Override
                public void onBuildQueueUpdated(List<BuildRequest> queue) {
                    adapter.setBuildQueue(star, colony);
                }
            });

            return v;
        }

        /**
         * This adapter is used to populate the list of buildings that are currently in progress.
         */
        private class BuildQueueListAdapter extends BaseAdapter {
            private List<BuildRequest> mQueue;

            public void setBuildQueue(Star star, Colony colony) {
                mQueue = new ArrayList<BuildRequest>();
                for (BuildRequest buildRequest : star.getBuildRequests()) {
                    if (buildRequest.getColonyKey().equals(colony.getKey())) {
                        mQueue.add(buildRequest);
                    }
                }

                notifyDataSetChanged();
            }

            @Override
            public int getCount() {
                if (mQueue == null)
                    return 0;
                return mQueue.size();
            }

            @Override
            public Object getItem(int position) {
                if (mQueue == null)
                    return null;
                return mQueue.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = convertView;
                if (view == null) {
                    LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService
                            (Context.LAYOUT_INFLATER_SERVICE);
                    view = inflater.inflate(R.layout.solarsystem_buildings_design, null);
                }

                ImageView icon = (ImageView) view.findViewById(R.id.building_icon);
                TextView row1 = (TextView) view.findViewById(R.id.building_row1);
                TextView row2 = (TextView) view.findViewById(R.id.building_row2);
                TextView row3 = (TextView) view.findViewById(R.id.building_row3);
                ProgressBar progress = (ProgressBar) view.findViewById(R.id.building_progress);

                BuildRequest request = mQueue.get(position);
                DesignManager dm = DesignManager.getInstance(request.getBuildKind());
                Design design = dm.getDesign(request.getDesignID());

                icon.setImageDrawable(new SpriteDrawable(design.getSprite()));

                if (request.getCount() == 1) {
                    row1.setText(design.getDisplayName());
                } else {
                    row1.setText(String.format("%s (× %d)",
                                               design.getDisplayName(),
                                               request.getCount()));
                }

                Duration remainingDuration = request.getRemainingTime();
                if (remainingDuration.equals(Duration.ZERO)) {
                    row2.setText(String.format(Locale.ENGLISH, "%d %%, not enough resources to complete.",
                                 (int) request.getPercentComplete()));
                } else {
                    row2.setText(String.format(Locale.ENGLISH, "%d %%, %s left",
                                 (int) request.getPercentComplete(),
                                 TimeInHours.format(remainingDuration)));
                }

                row3.setVisibility(View.GONE);
                progress.setVisibility(View.VISIBLE);
                progress.setProgress((int) request.getPercentComplete());

                return view;
            }
        }
    }
}
