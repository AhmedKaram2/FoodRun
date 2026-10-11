import React from 'react';
import {act,fireEvent,render,waitFor} from '@testing-library/react-native';
import {Platform} from 'react-native';
import AppUpdateSheet from '../src/components/AppUpdateSheet';
import FirstUseTour from '../src/components/FirstUseTour';
import {native} from '../src/native';
jest.mock('../src/native',()=>({native:{checkAppUpdate:jest.fn(),startAppUpdate:jest.fn(),getAppUpdateStatus:jest.fn(),installAppUpdate:jest.fn()}}));
const previous=Platform.OS;
afterEach(()=>{Object.defineProperty(Platform,'OS',{value:previous,configurable:true});jest.clearAllMocks();jest.useRealTimers();});
test('tour supports forward, back, finish and skip without navigating away',()=>{
 const complete=jest.fn(),view=render(<FirstUseTour visible rtl={false} onComplete={complete}/>);
 expect(view.getByText('Create or join a room')).toBeTruthy();fireEvent.press(view.getByText('Next'));expect(view.getByText('Choose together')).toBeTruthy();fireEvent.press(view.getByText('Back'));expect(view.getByText('Create or join a room')).toBeTruthy();
 for(let i=0;i<5;i++)fireEvent.press(view.getByText('Next'));
 fireEvent.press(view.getByText('Get started'));expect(complete).toHaveBeenCalledTimes(1);view.unmount();
 const skip=render(<FirstUseTour visible rtl={false} onComplete={complete}/>);fireEvent.press(skip.getByText('Skip tour'));expect(complete).toHaveBeenCalledTimes(2);
});
test('Android update download reaches installer once when the APK is ready',async()=>{
 jest.useFakeTimers();Object.defineProperty(Platform,'OS',{value:'android',configurable:true});
 const info={available:true,versionName:'1.8.0',state:'available',progress:0,canInstall:true};
 (native.checkAppUpdate as jest.Mock).mockResolvedValue(JSON.stringify(info));(native.startAppUpdate as jest.Mock).mockResolvedValue(JSON.stringify({...info,state:'downloading'}));(native.getAppUpdateStatus as jest.Mock).mockResolvedValue(JSON.stringify({...info,state:'ready',progress:1}));(native.installAppUpdate as jest.Mock).mockResolvedValue(JSON.stringify({...info,state:'installer'}));
 const view=render(<AppUpdateSheet rtl={false}/>);await act(async()=>{});fireEvent.press(view.getByText('Download update'));await act(async()=>{});
 expect(native.startAppUpdate).toHaveBeenCalledTimes(1);expect(native.installAppUpdate).not.toHaveBeenCalled();
 await act(async()=>{jest.advanceTimersByTime(1000);});await waitFor(()=>expect(native.installAppUpdate).toHaveBeenCalledTimes(1));
 await act(async()=>{jest.advanceTimersByTime(2000);});expect(native.installAppUpdate).toHaveBeenCalledTimes(1);
});
test('Later keeps a completed download from unexpectedly opening the installer',async()=>{
 jest.useFakeTimers();Object.defineProperty(Platform,'OS',{value:'android',configurable:true});
 const info={available:true,versionName:'1.8.0',state:'downloading',progress:.3,canInstall:true};
 (native.checkAppUpdate as jest.Mock).mockResolvedValue(JSON.stringify(info));(native.getAppUpdateStatus as jest.Mock).mockResolvedValue(JSON.stringify({...info,state:'ready'}));
 const view=render(<AppUpdateSheet rtl={false}/>);await act(async()=>{});fireEvent.press(view.getByText('Later'));await act(async()=>{jest.advanceTimersByTime(1000);});expect(native.installAppUpdate).not.toHaveBeenCalled();
});
