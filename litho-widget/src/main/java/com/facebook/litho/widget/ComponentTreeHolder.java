/*
 * Copyright (c) 2017-present, Facebook, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */

package com.facebook.litho.widget;

import android.support.v4.util.Pools;
import com.facebook.litho.Component;
import com.facebook.litho.ComponentContext;
import com.facebook.litho.ComponentTree;
import com.facebook.litho.LayoutHandler;
import com.facebook.litho.Size;
import com.facebook.litho.StateHandler;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * A class used to store the data backing a {@link RecyclerBinder}. For each item the
 * ComponentTreeHolder keeps the {@link RenderInfo} which contains the original {@link Component}
 * and either the {@link ComponentTree} or the {@link StateHandler} depending upon whether the item
 * is within the current working range or not.
 */
@ThreadSafe
public class ComponentTreeHolder {
  private static final Pools.SynchronizedPool<ComponentTreeHolder> sComponentTreeHoldersPool =
      new Pools.SynchronizedPool<>(8);

  @GuardedBy("this")
  private ComponentTree mComponentTree;

  @GuardedBy("this")
  private StateHandler mStateHandler;

  @GuardedBy("this")
  private RenderInfo mRenderInfo;

  private boolean mIsTreeValid;
  private LayoutHandler mLayoutHandler;
  private boolean mCanPrefetchDisplayLists;
  private boolean mCanCacheDrawingDisplayLists;

  public static ComponentTreeHolder acquire(
      RenderInfo renderInfo,
      LayoutHandler layoutHandler,
      boolean canPrefetchDisplayLists,
      boolean canCacheDrawingDisplayLists) {
    ComponentTreeHolder componentTreeHolder = sComponentTreeHoldersPool.acquire();
    if (componentTreeHolder == null) {
      componentTreeHolder = new ComponentTreeHolder();
    }
    componentTreeHolder.mRenderInfo = renderInfo;
    componentTreeHolder.mLayoutHandler = layoutHandler;
    componentTreeHolder.mCanPrefetchDisplayLists = canPrefetchDisplayLists;
    componentTreeHolder.mCanCacheDrawingDisplayLists = canCacheDrawingDisplayLists;
    return componentTreeHolder;
  }

  public synchronized void acquireStateHandlerAndReleaseTree() {
    acquireStateHandler();
    releaseTree();
  }

  synchronized void invalidateTree() {
    mIsTreeValid = false;
  }

  synchronized void clearStateHandler() {
    mStateHandler = null;
  }

  public void computeLayoutSync(
      ComponentContext context, int widthSpec, int heightSpec, Size size) {

    final ComponentTree componentTree;
    final Component component;
    final ComponentRenderInfo componentRenderInfo;

    synchronized (this) {
      ensureComponentTree(context);

      componentRenderInfo = (ComponentRenderInfo) mRenderInfo;
      componentTree = mComponentTree;
      component = componentRenderInfo.getComponent();
    }

    componentTree.setRootAndSizeSpec(component, widthSpec, heightSpec, size);

    synchronized (this) {
      if (componentTree == mComponentTree && component == componentRenderInfo.getComponent()) {
        mIsTreeValid = true;
      }
    }
  }

  public void computeLayoutAsync(ComponentContext context, int widthSpec, int heightSpec) {

    final ComponentTree componentTree;
    final Component component;
    final ComponentRenderInfo componentRenderInfo;

    synchronized (this) {
      ensureComponentTree(context);

      componentRenderInfo = (ComponentRenderInfo) mRenderInfo;
      componentTree = mComponentTree;
      component = componentRenderInfo.getComponent();
    }

    componentTree.setRootAndSizeSpecAsync(component, widthSpec, heightSpec);

    synchronized (this) {
      if (mComponentTree == componentTree && component == componentRenderInfo.getComponent()) {
        mIsTreeValid = true;
      }
    }
  }

  public synchronized RenderInfo getRenderInfo() {
    return mRenderInfo;
  }

  public synchronized boolean isTreeValid() {
    return mIsTreeValid;
  }

  public synchronized ComponentTree getComponentTree() {
    return mComponentTree;
  }

  public synchronized void setRenderInfo(RenderInfo renderInfo) {
    invalidateTree();
    mRenderInfo = renderInfo;
  }

  public synchronized void release() {
    releaseTree();
    clearStateHandler();
    mRenderInfo = null;
    mLayoutHandler = null;
    mCanPrefetchDisplayLists = false;
    mCanCacheDrawingDisplayLists = false;
    sComponentTreeHoldersPool.release(this);
  }

  @GuardedBy("this")
  private void ensureComponentTree(ComponentContext context) {
    if (mComponentTree == null) {
      final Object clipChildrenAttr = mRenderInfo.getCustomAttribute(RenderInfo.CLIP_CHILDREN);
      final boolean clipChildren = clipChildrenAttr == null ? true : (boolean) clipChildrenAttr;

      mComponentTree =
          ComponentTree.create(context, ((ComponentRenderInfo) mRenderInfo).getComponent())
              .layoutThreadHandler(mLayoutHandler)
              .stateHandler(mStateHandler)
              .canPrefetchDisplayLists(mCanPrefetchDisplayLists)
              .canCacheDrawingDisplayLists(mCanCacheDrawingDisplayLists)
              .shouldClipChildren(clipChildren)
              .build();
    }
  }

  @GuardedBy("this")
  private void releaseTree() {
    if (mComponentTree != null) {
      mComponentTree.release();
      mComponentTree = null;
    }

    mIsTreeValid = false;
  }

  @GuardedBy("this")
  private void acquireStateHandler() {
    if (mComponentTree == null) {
      return;
    }

    mStateHandler = mComponentTree.getStateHandler();
  }
}
