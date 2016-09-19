/*****************************************************************************
 * AudioBrowserFragment.java
 *****************************************************************************
 * Copyright © 2011-2012 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

package org.videolan.vlc.gui.audio;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TabLayout;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.TextView;

import org.videolan.libvlc.Media;
import org.videolan.libvlc.util.MediaBrowser;
import org.videolan.medialibrary.Medialibrary;
import org.videolan.medialibrary.interfaces.DevicesDiscoveryCb;
import org.videolan.medialibrary.media.Album;
import org.videolan.medialibrary.media.Artist;
import org.videolan.medialibrary.media.Genre;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.medialibrary.media.Playlist;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.gui.SecondaryActivity;
import org.videolan.vlc.gui.browser.MediaBrowserFragment;
import org.videolan.vlc.gui.dialogs.SavePlaylistDialog;
import org.videolan.vlc.gui.helpers.AudioUtil;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.video.MediaInfoFragment;
import org.videolan.vlc.gui.view.SwipeRefreshLayout;
import org.videolan.vlc.interfaces.IBrowser;
import org.videolan.vlc.media.MediaDatabase;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.FileUtils;
import org.videolan.vlc.util.Util;
import org.videolan.vlc.util.WeakHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

public class AudioBrowserFragment extends MediaBrowserFragment implements DevicesDiscoveryCb, SwipeRefreshLayout.OnRefreshListener, MediaBrowser.EventListener, IBrowser, ViewPager.OnPageChangeListener, AudioBrowserAdapter.ClickHandler {
    public final static String TAG = "VLC/AudioBrowserFragment";

    private MediaBrowser mMediaBrowser;
    private MainActivity mMainActivity;

    List<MediaWrapper> mAudioList;
    private AudioBrowserAdapter mArtistsAdapter;
    private AudioBrowserAdapter mAlbumsAdapter;
    private AudioBrowserAdapter mSongsAdapter;
    private AudioBrowserAdapter mGenresAdapter;
    private AudioBrowserAdapter mPlaylistAdapter;
    private ConcurrentLinkedQueue<AudioBrowserAdapter> mAdaptersToNotify = new ConcurrentLinkedQueue<>();

    private ViewPager mViewPager;
    private TabLayout mTabLayout;
    private TextView mEmptyView;
    private List<View> mLists;
    private FloatingActionButton mFabPlayShuffleAll;

    public static final int REFRESH = 101;
    public static final int UPDATE_LIST = 102;
    public final static int MODE_ARTIST = 0;
    public final static int MODE_ALBUM = 1;
    public final static int MODE_SONG = 2;
    public final static int MODE_GENRE = 3;
    public final static int MODE_PLAYLIST = 4;
    public final static int MODE_TOTAL = 5; // Number of audio browser modes

    public final static int MSG_LOADING = 0;
    private volatile boolean mDisplaying = false;

    /* All subclasses of Fragment must include a public empty constructor. */
    public AudioBrowserFragment() { }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSongsAdapter = new AudioBrowserAdapter(getActivity(), this, true);
        mArtistsAdapter = new AudioBrowserAdapter(getActivity(), this, true);
        mAlbumsAdapter = new AudioBrowserAdapter(getActivity(), this, true);
        mGenresAdapter = new AudioBrowserAdapter(getActivity(), this, true);
        mPlaylistAdapter = new AudioBrowserAdapter(getActivity(), this, true);

//        mSongsAdapter.setContextPopupMenuListener(mContextPopupMenuListener);
//        mArtistsAdapter.setContextPopupMenuListener(mContextPopupMenuListener);
//        mAlbumsAdapter.setContextPopupMenuListener(mContextPopupMenuListener);
//        mGenresAdapter.setContextPopupMenuListener(mContextPopupMenuListener);
//        mPlaylistAdapter.setContextPopupMenuListener(mContextPopupMenuListener);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.audio_browser, container, false);

        mEmptyView = (TextView) v.findViewById(R.id.no_media);

        RecyclerView songsList = (RecyclerView)v.findViewById(R.id.songs_list);
        RecyclerView artistList = (RecyclerView)v.findViewById(R.id.artists_list);
        RecyclerView albumList = (RecyclerView)v.findViewById(R.id.albums_list);
        RecyclerView genreList = (RecyclerView)v.findViewById(R.id.genres_list);
        RecyclerView playlistsList = (RecyclerView)v.findViewById(R.id.playlists_list);

        songsList.setLayoutManager(new LinearLayoutManager(getActivity()));
        songsList.setAdapter(mSongsAdapter);
        artistList.setLayoutManager(new LinearLayoutManager(getActivity()));
        artistList.setAdapter(mArtistsAdapter);
        albumList.setLayoutManager(new LinearLayoutManager(getActivity()));
        albumList.setAdapter(mAlbumsAdapter);
        genreList.setLayoutManager(new LinearLayoutManager(getActivity()));
        genreList.setAdapter(mGenresAdapter);
        playlistsList.setLayoutManager(new LinearLayoutManager(getActivity()));
        playlistsList.setAdapter(mPlaylistAdapter);

        mLists = Arrays.asList((View)artistList, albumList, songsList, genreList, playlistsList);
        String[] titles = new String[] {getString(R.string.artists), getString(R.string.albums),
                getString(R.string.songs), getString(R.string.genres), getString(R.string.playlists)};
        mViewPager = (ViewPager) v.findViewById(R.id.pager);
        mViewPager.setOffscreenPageLimit(MODE_TOTAL - 1);
        mViewPager.setAdapter(new AudioPagerAdapter(mLists, titles));

        mViewPager.setOnTouchListener(mSwipeFilter);

        mTabLayout = (TabLayout) v.findViewById(R.id.sliding_tabs);
        setupTabLayout();

        artistList.setOnKeyListener(keyListener);
        albumList.setOnKeyListener(keyListener);
        songsList.setOnKeyListener(keyListener);
        genreList.setOnKeyListener(keyListener);
        playlistsList.setOnKeyListener(keyListener);

        registerForContextMenu(songsList);
        registerForContextMenu(artistList);
        registerForContextMenu(albumList);
        registerForContextMenu(genreList);
        registerForContextMenu(playlistsList);

        mSwipeRefreshLayout = (SwipeRefreshLayout) v.findViewById(R.id.swipeLayout);

        mSwipeRefreshLayout.setColorSchemeResources(R.color.orange700);
        mSwipeRefreshLayout.setOnRefreshListener(this);

        songsList.addOnScrollListener(mRVScrollListener);
        artistList.addOnScrollListener(mRVScrollListener);
        albumList.addOnScrollListener(mRVScrollListener);
        genreList.addOnScrollListener(mRVScrollListener);
        playlistsList.addOnScrollListener(mRVScrollListener);

        mFabPlayShuffleAll = (FloatingActionButton)v.findViewById(R.id.fab_play_shuffle_all);
        mFabPlayShuffleAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onFabPlayAllClick(v);
            }
        });
        setFabPlayShuffleAllVisibility();

        return v;
    }

    private void setupTabLayout() {
        final PagerAdapter adapter = mViewPager.getAdapter();
        mTabLayout.setTabsFromPagerAdapter(adapter);
        mViewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(mTabLayout));
        mTabLayout.setOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                mViewPager.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                ((RecyclerView)mLists.get(tab.getPosition())).smoothScrollToPosition(0);
            }
        });
    }

    RecyclerView.OnScrollListener mRVScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            super.onScrollStateChanged(recyclerView, newState);
        }

        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            int topRowVerticalPosition =
                    (recyclerView == null || recyclerView.getChildCount() == 0) ? 0 : recyclerView.getChildAt(0).getTop();
            mSwipeRefreshLayout.setEnabled(topRowVerticalPosition >= 0);
        }
    };

    @Override
    public void onPause() {
        super.onPause();

        mViewPager.removeOnPageChangeListener(this);
        //TODO
//        mMediaLibrary.removeUpdateHandler(mHandler);
//        mMediaLibrary.setBrowser(null);
        if (mMediaBrowser != null) {
            mMediaBrowser.release();
            mMediaBrowser = null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mMainActivity = (MainActivity) getActivity();

        mViewPager.addOnPageChangeListener(this);
        if (mMediaLibrary.isWorking()) {
            mMediaLibrary.addDeviceDiscoveryCb(this);
            mHandler.sendEmptyMessageDelayed(MSG_LOADING, 300);
        }
        else if (mGenresAdapter.isEmpty() || mArtistsAdapter.isEmpty() ||
                mAlbumsAdapter.isEmpty() || mSongsAdapter.isEmpty())
            updateLists();
        else {
            updateEmptyView(mViewPager.getCurrentItem());
            updatePlaylists();
        }
        final View current = mLists.get(mViewPager.getCurrentItem());
        if (current instanceof RecyclerView) {
            current.post(new Runnable() {
                @Override
                public void run() {
                    mSwipeRefreshLayout.setEnabled(((LinearLayoutManager)((RecyclerView)current).getLayoutManager()).findFirstVisibleItemPosition() == 0);
                }
            });
        }
    }

    // Focus support. Start.
    View.OnKeyListener keyListener = new View.OnKeyListener() {
        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {

            /* Qualify key action to prevent redundant event
             * handling.
             */
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                int newPosition = mViewPager.getCurrentItem();

                switch (event.getKeyCode()) {
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        if (newPosition < (MODE_TOTAL - 1))
                            newPosition++;
                        break;
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                        if (newPosition > 0)
                            newPosition--;
                        break;
                    default:
                        return false;
                }

                if (newPosition != mViewPager.getCurrentItem()) {
                    mViewPager.setCurrentItem(newPosition);
                }
            }

            // clean up with MainActivity
            return false;
        }
    };
    // Focus support. End.


//    OnItemClickListener playlistListener = new OnItemClickListener() {
//        @Override
//        public void onItemClick(AdapterView<?> av, View v, int p, long id) {
//            loadPlaylist(p);
//        }
//    };

    private void loadPlaylist(int position) {
        MediaWrapper[] mediaList = ((Playlist)mPlaylistAdapter.getItem(position)).getTracks(mMediaLibrary);
        if (mService == null)
            return;
        mService.load(mediaList, 0);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.audio_list_browser, menu);

        int position = ((AdapterContextMenuInfo) menuInfo).position;
        setContextMenuItems(menu, position);
    }

    private void setContextMenuItems(Menu menu, int position) {
        final int pos = mViewPager.getCurrentItem();
        if (pos != MODE_SONG) {
            menu.setGroupVisible(R.id.songs_view_only, false);
            menu.setGroupVisible(R.id.phone_only, false);
        }
        if (pos == MODE_ARTIST || pos == MODE_GENRE || pos == MODE_ALBUM)
            menu.findItem(R.id.audio_list_browser_play).setVisible(true);
        if (pos != MODE_SONG && pos != MODE_PLAYLIST)
            menu.findItem(R.id.audio_list_browser_delete).setVisible(false);
        else {
            MenuItem item = menu.findItem(R.id.audio_list_browser_delete);
            AudioBrowserAdapter adapter = pos == MODE_SONG ? mSongsAdapter : mPlaylistAdapter;
            MediaLibraryItem mediaItem = adapter.getItem(position);
            if (pos == MODE_PLAYLIST )
                item.setVisible(true);
            else if (pos == MODE_SONG){
                String location = ((MediaWrapper)mediaItem).getLocation();
                item.setVisible(FileUtils.canWrite(location));
            }
        }
        if (!AndroidDevices.isPhone())
            menu.setGroupVisible(R.id.phone_only, false);
    }

    @Override
    public boolean onContextItemSelected(MenuItem menu) {
        if(!getUserVisibleHint())
            return super.onContextItemSelected(menu);

        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menu.getMenuInfo();
        if (info != null && handleContextItemSelected(menu, info.position))
            return true;
        return super.onContextItemSelected(menu);
    }

    private boolean handleContextItemSelected(final MenuItem item, final int position) {
        final AudioBrowserAdapter adapter;
        int mode = mViewPager.getCurrentItem();
        switch (mode) {
            case MODE_SONG:
                adapter = mSongsAdapter;
                break;
            case MODE_ALBUM:
                adapter = mAlbumsAdapter;
                break;
            case MODE_ARTIST:
                adapter = mArtistsAdapter;
                break;
            case MODE_PLAYLIST:
                adapter = mPlaylistAdapter;
                break;
            case MODE_GENRE:
                adapter = mGenresAdapter;
                break;
            default:
                return false;
        }
        if (position < 0 && position >= adapter.getItemCount())
            return false;

        int id = item.getItemId();
        MediaLibraryItem mediaItem = adapter.getItem(position);

        if (id == R.id.audio_list_browser_delete) {
            final MediaLibraryItem mediaLibraryItem = adapter.getItem(position);
            String message;
            Runnable action;

            adapter.remove(position);

            if (mode == MODE_PLAYLIST) {
                message = getString(R.string.playlist_deleted);
                action = new Runnable() {
                    @Override
                    public void run() {
                        deletePlaylist((Playlist) mediaLibraryItem);
                    }
                };
            } else if (mode == MODE_SONG) {
                message = getString(R.string.file_deleted);
                action = new Runnable() {
                    @Override
                    public void run() {
                        deleteMedia((MediaWrapper) mediaLibraryItem);
                    }
                };
            } else
                return false;
            UiTools.snackerWithCancel(getView(), message, action, new Runnable() {
                @Override
                public void run() {
                    adapter.addItem(position, mediaLibraryItem);
                }
            });
            return true;
        }

        if (id == R.id.audio_list_browser_set_song) {
            if (mSongsAdapter.getItemCount() <= position)
                return false;
            AudioUtil.setRingtone((MediaWrapper) mSongsAdapter.getItem(position), getActivity());
            return true;
        }

        if (id == R.id.audio_view_info) {
            Intent i = new Intent(getActivity(), SecondaryActivity.class);
            i.putExtra(SecondaryActivity.KEY_FRAGMENT, SecondaryActivity.MEDIA_INFO);
            i.putExtra(MediaInfoFragment.ITEM_KEY, mSongsAdapter.getItem(position));
            getActivity().startActivityForResult(i, MainActivity.ACTIVITY_RESULT_SECONDARY);
            return true;
        }

        if (id == R.id.audio_view_add_playlist) {
            FragmentManager fm = getActivity().getSupportFragmentManager();
            SavePlaylistDialog savePlaylistDialog = new SavePlaylistDialog();
            Bundle args = new Bundle();
            args.putParcelableArrayList(SavePlaylistDialog.KEY_NEW_TRACKS, (ArrayList<MediaWrapper>) new ArrayList<>(Arrays.asList(mediaItem.getTracks(mMediaLibrary))));
            savePlaylistDialog.setArguments(args);
            savePlaylistDialog.setCallBack(updatePlaylists);
            savePlaylistDialog.show(fm, "fragment_add_to_playlist");
            return true;
        }

        int startPosition;
        MediaLibraryItem[] medias;

        boolean useAllItems = id == R.id.audio_list_browser_play_all;
        boolean append = id == R.id.audio_list_browser_append;

        // Play/Append
        if (useAllItems) {
            if (mSongsAdapter.getItemCount() <= position)
                return false;
            ArrayList<MediaLibraryItem> mediaList = new ArrayList<>();
            startPosition = mSongsAdapter.getListWithPosition(mediaList, position);
            medias = mediaList.toArray(new MediaLibraryItem[mediaList.size()]);
        } else {
            startPosition = 0;
            if (position >= adapter.getItemCount())
                return false;
            medias = mediaItem.getTracks(mMediaLibrary);
        }

        if (mService != null) {
            if (append)
                mService.append(Arrays.asList((MediaWrapper[]) medias));
            else
                mService.load((MediaWrapper[]) medias, startPosition);
            return true;
        } else
            return false;
    }

    public void onFabPlayAllClick(View view) {
        MediaWrapper[] list = (MediaWrapper[]) mSongsAdapter.getMediaItems().toArray();
        int count = list.length;
        if (count > 0) {
            Random rand = new Random();
            int randomSong = rand.nextInt(count);
            if (mService != null) {
                mService.load(list, randomSong);
                mService.shuffle();
            }
        }
    }

    public void setFabPlayShuffleAllVisibility() {
        if (mViewPager.getCurrentItem() == MODE_SONG)
            mFabPlayShuffleAll.setVisibility(View.VISIBLE);
        else
            mFabPlayShuffleAll.setVisibility(View.GONE);
    }

    /**
     * Handle changes on the list
     */
    private Handler mHandler = new AudioBrowserHandler(this);

    @Override
    public void onRefresh() {
        if (!Medialibrary.getInstance(VLCApplication.getAppContext()).isWorking())
            Medialibrary.getInstance(VLCApplication.getAppContext()).nativeReload();
    }

    @Override
    public void setReadyToDisplay(boolean ready) {
        if (Util.isListEmpty(mAdaptersToNotify))
            mReadyToDisplay = ready;
        else
            display();
    }

    @Override
    public void display() {
        mReadyToDisplay = true;
        if (mAdaptersToNotify.isEmpty())
            return;
        mDisplaying = true;
        if (getActivity() != null)
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    for (AudioBrowserAdapter adapter : mAdaptersToNotify)
                        adapter.notifyDataSetChanged();
                    mAdaptersToNotify.clear();
                    mHandler.removeMessages(MSG_LOADING);
                    mSwipeRefreshLayout.setRefreshing(false);
                    mDisplaying = false;
                    updateEmptyView(mViewPager.getCurrentItem());
                }
            });
    }

    @Override
    protected String getTitle() {
        return getString(R.string.audio);
    }

    private void updateEmptyView(int position) {
        if (position == MODE_PLAYLIST){
            mEmptyView.setVisibility(mPlaylistAdapter.isEmpty() ? View.VISIBLE : View.GONE);
            mEmptyView.setText(R.string.noplaylist);
        } else {
            mEmptyView.setVisibility(mAudioList == null || mAudioList.isEmpty() ? View.VISIBLE : View.GONE);
            mEmptyView.setText(R.string.nomedia);
        }
    }

    ArrayList<MediaWrapper> mTracksToAppend = new ArrayList<MediaWrapper>(); //Playlist tracks to append

    @Override
    public void onMediaAdded(int index, Media media) {
        mTracksToAppend.add(new MediaWrapper(media));
    }

    @Override
    public void onMediaRemoved(int index, Media media) {}

    @Override
    public void onBrowseEnd() {
        if (mService != null)
            mService.append(mTracksToAppend);
    }

    @Override
    public void showProgressBar() {
        mMainActivity.showProgressBar();
    }

    @Override
    public void hideProgressBar() {
        mMainActivity.hideProgressBar();
    }

    @Override
    public void clearTextInfo() {
        mMainActivity.clearTextInfo();
    }

    @Override
    public void sendTextInfo(String info, int progress, int max) {
        mMainActivity.sendTextInfo(info, progress, max);
    }

    TabLayout.TabLayoutOnPageChangeListener tcl = new TabLayout.TabLayoutOnPageChangeListener(mTabLayout);

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        tcl.onPageScrolled(position, positionOffset, positionOffsetPixels);
    }

    @Override
    public void onPageSelected(int position) {
        updateEmptyView(position);
        setFabPlayShuffleAllVisibility();
    }

    private void deleteMedia(final MediaWrapper mw) {
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                final String path = mw.getUri().getPath();
                FileUtils.deleteFile(path);
                MediaDatabase.getInstance().removeMedia(mw.getUri());
                mMediaLibrary.nativeReload(FileUtils.getParent(mw.getUri().getPath()));
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mService != null)
                            mService.removeLocation(mw.getLocation());
                    }
                });
                mHandler.obtainMessage(REFRESH, path).sendToTarget();
            }
        });
    }

    private void deletePlaylist(final Playlist playlist) {
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                playlist.delete(mMediaLibrary);
                mHandler.obtainMessage(UPDATE_LIST).sendToTarget();
            }
        });
    }

    @Override
    public void onPageScrollStateChanged(int state) {
        tcl.onPageScrollStateChanged(state);
    }

    @Override
    public void onClick(View v, int position, MediaLibraryItem item) {
        if (item instanceof Artist || item instanceof Genre) {
            Intent i = new Intent(getActivity(), SecondaryActivity.class);
            i.putExtra(SecondaryActivity.KEY_FRAGMENT, SecondaryActivity.ALBUMS_SONGS);
            i.putExtra(AudioAlbumsSongsFragment.TAG_ITEM, item);
            startActivity(i);
        } else if (item instanceof MediaWrapper) {
            mService.load((MediaWrapper) item);
        } else if (item instanceof Album) {
            Intent i = new Intent(getActivity(), SecondaryActivity.class);
            i.putExtra(SecondaryActivity.KEY_FRAGMENT, SecondaryActivity.ALBUM);
            i.putExtra(AudioAlbumFragment.TAG_ITEM, item);
            startActivity(i);
        } else if (item instanceof Playlist) {
            mService.load(item.getTracks(mMediaLibrary), 0);
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    public void onCtxClick(View anchor, final int position, MediaLibraryItem item) {
//        if (!AndroidUtil.isHoneycombOrLater()) {
//            // Call the "classic" context menu
//            anchor.performLongClick();
//            return;
//        }

        PopupMenu popupMenu = new PopupMenu(getActivity(), anchor);
        popupMenu.getMenuInflater().inflate(R.menu.audio_list_browser, popupMenu.getMenu());
        setContextMenuItems(popupMenu.getMenu(), position);

        popupMenu.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                return handleContextItemSelected(item, position);
            }
        });
        popupMenu.show();
    }

private static class AudioBrowserHandler extends WeakHandler<AudioBrowserFragment> {
    public AudioBrowserHandler(AudioBrowserFragment owner) {
        super(owner);
    }

    @Override
    public void handleMessage(Message msg) {
        final AudioBrowserFragment fragment = getOwner();
        if(fragment == null) return;

        switch (msg.what) {
            case MSG_LOADING:
                if (fragment.mArtistsAdapter.isEmpty() && fragment.mAlbumsAdapter.isEmpty() &&
                        fragment.mSongsAdapter.isEmpty() && fragment.mGenresAdapter.isEmpty())
                    fragment.mSwipeRefreshLayout.setRefreshing(true);
                break;
            case REFRESH:
                refresh(fragment, (String) msg.obj);
                break;
            case UPDATE_LIST:
                fragment.updateLists();
                break;
        }
    }

    private void refresh(AudioBrowserFragment fragment, String path) {
        if (fragment.mService == null)
            return;

        final List<String> mediaLocations = fragment.mService.getMediaLocations();
        if (mediaLocations != null && mediaLocations.contains(path))
            fragment.mService.removeLocation(path);
        fragment.updateLists();
    }
}

    private void updateLists() {
        final Medialibrary ml = Medialibrary.getInstance(VLCApplication.getAppContext());
        MediaWrapper[] audioItems = ml.nativeGetAudio();
        mAudioList = new ArrayList<>(Arrays.asList(audioItems));
        if (mAudioList.isEmpty()){
            updateEmptyView(mViewPager.getCurrentItem());
            mSwipeRefreshLayout.setRefreshing(false);
            mTabLayout.setVisibility(View.GONE);
        } else {
            mTabLayout.setVisibility(View.VISIBLE);
            mHandler.sendEmptyMessageDelayed(MSG_LOADING, 300);

            final ArrayList<Runnable> tasks = new ArrayList<Runnable>(Arrays.asList(updateArtists,
                    updateAlbums, updateSongs, updateGenres, updatePlaylists));

            //process the visible list first
            if (mViewPager.getCurrentItem() != 0)
                tasks.add(0, tasks.remove(mViewPager.getCurrentItem()));
            tasks.add(new Runnable() {
                @Override
                public void run() {
                    if (!mAdaptersToNotify.isEmpty())
                        display();
                }
            });
            VLCApplication.runBackground(new Runnable() {
                @Override
                public void run() {
                    for (Runnable task : tasks)
                        task.run();
                }
            });
        }
    }

    Runnable updateArtists = new Runnable() {
        @Override
        public void run() {
            mArtistsAdapter.addAll(mMediaLibrary.nativeGetArtists());
            mAdaptersToNotify.add(mArtistsAdapter);
            if (mReadyToDisplay && !mDisplaying)
                display();
        }
    };

    Runnable updateAlbums = new Runnable() {
        @Override
        public void run() {
            mAlbumsAdapter.addAll(mMediaLibrary.nativeGetAlbums());
            mAdaptersToNotify.add(mAlbumsAdapter);
            if (mReadyToDisplay && !mDisplaying)
                display();
        }
    };

    Runnable updateSongs = new Runnable() {
        @Override
        public void run() {
            mSongsAdapter.addAll(mMediaLibrary.nativeGetAudio());
            mAdaptersToNotify.add(mSongsAdapter);
            if (mReadyToDisplay && !mDisplaying)
                display();
        }
    };

    Runnable updateGenres = new Runnable() {
        @Override
        public void run() {
            mGenresAdapter.addAll(mMediaLibrary.nativeGetGenres());
            mAdaptersToNotify.add(mGenresAdapter);
            if (mReadyToDisplay && !mDisplaying)
                display();
        }
    };

    //TODO
    Runnable updatePlaylists = new Runnable() {
        @Override
        public void run() {
            //DB playlists
            mPlaylistAdapter.addAll(mMediaLibrary.nativeGetPlaylists());
            mAdaptersToNotify.add(mPlaylistAdapter);
            if (mReadyToDisplay && !mDisplaying)
                display();
        }
    };

    private void updatePlaylists() {
        VLCApplication.runBackground(updatePlaylists);
    }

    /*
     * Disable Swipe Refresh while scrolling horizontally
     */
    private View.OnTouchListener mSwipeFilter = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            mSwipeRefreshLayout.setEnabled(false);
            switch (event.getAction()) {
                case MotionEvent.ACTION_UP:
                    mSwipeRefreshLayout.setEnabled(true);
                    break;
            }
            return false;
        }
    };

    public void clear(){
        mGenresAdapter.clear();
        mArtistsAdapter.clear();
        mAlbumsAdapter.clear();
        mSongsAdapter.clear();
        mPlaylistAdapter.clear();
    }

    @Override
    public void onDiscoveryStarted(String entryPoint) {}

    @Override
    public void onDiscoveryProgress(String entryPoint) {}

    @Override
    public void onDiscoveryCompleted(String entryPoint) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mSwipeRefreshLayout.setRefreshing(false);
            }
        });
        mMediaLibrary.removeDeviceDiscoveryCb(this);
        updateLists();
    }

    @Override
    public void onParsingStatsUpdated(int percent) {}
}
