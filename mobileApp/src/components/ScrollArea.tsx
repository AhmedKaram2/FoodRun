import React, { createContext, forwardRef, useCallback, useContext, useEffect, useImperativeHandle, useMemo, useRef } from "react";
import { ScrollView, type ScrollViewProps, View } from "react-native";

type Check = (top:number,bottom:number) => void;
const ScrollContext = createContext<{subscribe:(check:Check)=>()=>void;refresh:()=>void}|null>(null);

/** Measures load markers against their nearest scrolling viewport, including sheets. */
const ScrollArea = forwardRef<ScrollView,ScrollViewProps>(function ScrollArea(props,ref) {
  const scroll = useRef<ScrollView>(null), checks = useRef(new Set<Check>()), frame = useRef<number|null>(null);
  useImperativeHandle(ref,() => scroll.current!,[]);
  const refresh = useCallback(() => {
    if(frame.current !== null) return;
    frame.current = requestAnimationFrame(() => {
      frame.current=null;
      scroll.current?.getNativeScrollRef()?.measureInWindow((_x:number,y:number,width:number,height:number) => {
        if(width > 0 && height > 0) checks.current.forEach(check => check(y,y+height));
      });
    });
  },[]);
  useEffect(() => () => { if(frame.current !== null) cancelAnimationFrame(frame.current); },[]);
  const context = useMemo(() => ({refresh,subscribe:(check:Check) => { checks.current.add(check); refresh(); return () => { checks.current.delete(check); }; }}),[refresh]);
  if(props.horizontal) return <ScrollView {...props} ref={scroll}/>;
  return <ScrollContext.Provider value={context}><ScrollView {...props} ref={scroll} scrollEventThrottle={100}
    onScroll={event => { props.onScroll?.(event); refresh(); }}
    onLayout={event => { props.onLayout?.(event); refresh(); }}
    onContentSizeChange={(width,height) => { props.onContentSizeChange?.(width,height); refresh(); }}/></ScrollContext.Provider>;
});
export default ScrollArea;

export function ScrollLoad({loadKey,enabled=true,onLoad,children}:{loadKey:string|number;enabled?:boolean;onLoad:()=>void;children?:React.ReactNode}) {
  const context = useContext(ScrollContext), marker = useRef<View>(null), requested = useRef<string|number|null>(null);
  const latest = useRef({loadKey,enabled,onLoad}); latest.current={loadKey,enabled,onLoad};
  useEffect(() => context?.subscribe((top,bottom) => {
    const value=latest.current;
    if(!value.enabled || requested.current === value.loadKey) return;
    marker.current?.measureInWindow((_x,y,width,height) => {
      if(width <= 0 || y > bottom+120 || y+height < top || !latest.current.enabled || latest.current.loadKey !== value.loadKey || requested.current === value.loadKey) return;
      requested.current=value.loadKey; value.onLoad();
    });
  }),[context]);
  useEffect(() => { context?.refresh(); },[context,loadKey,enabled]);
  return <View ref={marker} collapsable={false} testID="scroll-load" onLayout={() => context?.refresh()} style={{minHeight:2}}>{children}</View>;
}
