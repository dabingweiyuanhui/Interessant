package me.zsj.interessant;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.jakewharton.rxbinding.support.v4.widget.RxSwipeRefreshLayout;
import com.jakewharton.rxbinding.support.v7.widget.RxRecyclerView;

import java.util.ArrayList;
import java.util.List;

import me.drakeet.multitype.Item;
import me.drakeet.multitype.MultiTypeAdapter;
import me.zsj.interessant.api.DailyApi;
import me.zsj.interessant.base.ToolbarActivity;
import me.zsj.interessant.common.OnMovieClickListener;
import me.zsj.interessant.interesting.InterestingActivity;
import me.zsj.interessant.model.Category;
import me.zsj.interessant.model.Daily;
import me.zsj.interessant.model.ItemList;
import me.zsj.interessant.provider.DailyItemViewProvider;
import me.zsj.interessant.rx.RxScroller;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

public class MainActivity extends ToolbarActivity {

    public static final String PROVIDER_ITEM = "item";
    public static final String CATEGORY_ID = "categoryId";
    public static final String TITLE = "title";

    private MultiTypeAdapter multiTypeAdapter;

    private RecyclerView list;
    private SwipeRefreshLayout refreshLayout;
    private DrawerLayout drawer;

    private DailyApi dailyApi;
    private List<Item> items = new ArrayList<>();
    private String dateTime = "";


    @Override
    public int providerLayoutId() {
        return R.layout.main_activity;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        list = (RecyclerView) findViewById(R.id.list);
        refreshLayout = (SwipeRefreshLayout) findViewById(R.id.refreshLayout);
        ab.setHomeAsUpIndicator(R.drawable.ic_menu);

        drawer = (DrawerLayout) findViewById(R.id.drawer_layout);

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        setupDrawerContent(navigationView);

        dailyApi = InteressantFactory.getRetrofit().createApi(DailyApi.class);
        setupRecyclerView();

        RxSwipeRefreshLayout.refreshes(refreshLayout)
                .compose(this.<Void>bindToLifecycle())
                .subscribe(new Action1<Void>() {
                    @Override
                    public void call(Void aVoid) {
                        loadData(true);
                    }
                });

    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        loadData(true);
    }

    private void setupRecyclerView() {
        final LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        multiTypeAdapter = new MultiTypeAdapter(items);
        list.setLayoutManager(layoutManager);
        list.setAdapter(multiTypeAdapter);

        RxRecyclerView.scrollStateChanges(list)
                .filter(new Func1<Integer, Boolean>() {
                    @Override
                    public Boolean call(Integer integer) {
                        return !refreshLayout.isRefreshing();
                    }
                })
                .compose(this.<Integer>bindToLifecycle())
                .compose(RxScroller.scrollTransformer(layoutManager,
                        multiTypeAdapter, items))
                .subscribe(new Action1<Integer>() {
                    @Override
                    public void call(Integer newState) {
                        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                            loadData();
                        }
                    }
                });

        DailyItemViewProvider dailyItemViewProvider = (DailyItemViewProvider) App.getProvider(PROVIDER_ITEM);
        dailyItemViewProvider.setOnMovieClickListener(new OnMovieClickListener() {
            @Override
            public void onMovieClick(final ItemList item, final View movieAlbum) {
                IntentManager.flyToMovieDetail(MainActivity.this,
                        item, movieAlbum);
            }
        });
    }

    private void loadData() {
        loadData(false /*Load more data. */);
    }

    private void loadData(final boolean clear) {
        Observable<Daily> result;
        if (clear) result = dailyApi.getDaily();
        else result = dailyApi.getDaily(Long.decode(dateTime));

        result.compose(this.<Daily>bindToLifecycle())
                .filter(new Func1<Daily, Boolean>() {
                    @Override
                    public Boolean call(Daily daily) {
                        return daily != null;
                    }
                })
                .doOnNext(new Action1<Daily>() {
                    @Override
                    public void call(Daily daily) {
                        if (clear) items.clear();
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnCompleted(new Action0() {
                    @Override
                    public void call() {
                        refreshLayout.setRefreshing(false);
                    }
                })
                .subscribe(new Action1<Daily>() {
                    @Override
                    public void call(Daily daily) {
                        refreshLayout.setRefreshing(false);
                        addData(daily);
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        refreshLayout.setRefreshing(false);
                        Toast.makeText(MainActivity.this, throwable.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void addData(Daily daily) {
        for (Daily.IssueList issueList : daily.issueList) {
            String date = issueList.itemList.get(0).data.text;
            items.add(new Category(date == null ? "Today" : date));
            for (ItemList itemList : issueList.itemList) {
                items.add(itemList);
            }
        }
        String nextPageUrl = daily.nextPageUrl;
        dateTime = nextPageUrl.substring(nextPageUrl.indexOf("=") + 1,
                nextPageUrl.indexOf("&"));
        multiTypeAdapter.notifyDataSetChanged();
    }

    private void setupDrawerContent(NavigationView navigationView) {
        navigationView.setNavigationItemSelectedListener(
                new NavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
                        menuItem.setChecked(true);
                        drawer.closeDrawers();
                        findInteresting(menuItem);
                        return true;
                    }
                });
    }

    private void findInteresting(MenuItem item) {
        int id;
        String title;
        switch (item.getItemId()) {
            case R.id.nav_cute_pet:
                id = 26;
                title = getResources().getString(R.string.cute_pet);
                break;
            case R.id.nav_funny:
                id = 28;
                title = getResources().getString(R.string.funny);
                break;
            case R.id.nav_game:
                id = 30;
                title = getResources().getString(R.string.game);
                break;
            case R.id.nav_science:
                id = 32;
                title = getResources().getString(R.string.science);
                break;
            case R.id.nav_highlights:
                id = 34;
                title = getResources().getString(R.string.highlights);
                break;
            case R.id.nav_life:
                id = 36;
                title = getResources().getString(R.string.life);
                break;
            case R.id.nav_variety:
                id = 38;
                title = getResources().getString(R.string.variety);
                break;
            case R.id.nav_eating:
                id = 4;
                title = getResources().getString(R.string.eating);
                break;
            case R.id.nav_foreshow:
                id = 8;
                title = getResources().getString(R.string.foreshow);
                break;
            case R.id.nav_ad:
                id = 14;
                title = getResources().getString(R.string.advertisement);
                break;
            case R.id.nav_record:
                id = 22;
                title = getResources().getString(R.string.record);
                break;
            case R.id.nav_fashion:
                id = 24;
                title = getResources().getString(R.string.fashion);
                break;
            case R.id.nav_creative:
                id = 2;
                title = getResources().getString(R.string.creative);
                break;
            case R.id.nav_sports:
                id = 18;
                title = getResources().getString(R.string.sports);
                break;
            case R.id.nav_journey:
                id = 6;
                title = getResources().getString(R.string.journey);
                break;
            case R.id.nav_story:
                id = 12;
                title = getResources().getString(R.string.story);
                break;
            case R.id.nav_cartoon:
                id = 10;
                title = getResources().getString(R.string.cartoon);
                break;
            case R.id.nav_music:
                id = 20;
                title = getResources().getString(R.string.music);
                break;
            default:
                return;
        }
        Intent intent = new Intent(this, InterestingActivity.class);
        intent.putExtra(CATEGORY_ID, id);
        intent.putExtra(TITLE, title);
        startActivity(intent);
    }

    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            drawer.openDrawer(GravityCompat.START);
            return true;
        } else if (item.getItemId() == R.id.search_action) {
            toSearch(this);
        }
        return super.onOptionsItemSelected(item);
    }

}
