import React from 'react';
import {act,fireEvent,render} from '@testing-library/react-native';
import QuickWheel from '../src/screens/QuickWheel';
import LiveSelectionOverlay from '../src/components/LiveSelectionOverlay';
import {quickAction} from '../src/native';
import type {QuickWheel as QuickState,Wheel} from '../src/types';
jest.mock('../src/native',()=>({quickAction:jest.fn()}));
const state:QuickState={people:[{id:'user:a',name:'Alice',active:true,removable:true},{id:'user:b',name:'Bob',active:false,removable:true}],history:[],winner:'',spinning:false,canSpin:true,startRotation:0,endRotation:0,destination:'MAIN',nameDraft:'',error:'',revision:1,groups:[{id:'owner:g',name:'Team',count:3}],purpose:'Prize draw',style:'names'};
afterEach(()=>{jest.clearAllMocks();jest.useRealTimers();});
test('general purpose, participant edits and removal are available for registered people',()=>{
  const view=render(<QuickWheel state={{...state,destination:'CREW'}} rtl={false}/>);
  expect(view.getByText('Quick Choice')).toBeTruthy();expect(view.getAllByText('Prize draw').length).toBeGreaterThan(0);
  fireEvent.press(view.getByRole('checkbox',{name:'Bob'}));expect(quickAction).toHaveBeenCalledWith('toggle','user:b');
  fireEvent.press(view.getByRole('button',{name:'Remove Alice'}));expect(quickAction).toHaveBeenCalledWith('remove','user:a');
});
test('groups are selectable from a searchable sheet',()=>{
  const view=render(<QuickWheel state={{...state,destination:'GROUPS'}} rtl={false}/>);
  fireEvent.changeText(view.getByLabelText('Search groups'),'Team');fireEvent.press(view.getByText('Team'));
  expect(quickAction).toHaveBeenCalledWith('group','owner:g');
});
test('custom purpose, wheel style and online audience can be selected',()=>{
  const view=render(<QuickWheel state={{...state,destination:'SETTINGS'}} rtl={false}/>);
  fireEvent.changeText(view.getByLabelText('Choosing for'),'Game captain');expect(quickAction).toHaveBeenCalledWith('purpose','Game captain');
  fireEvent.press(view.getByText('Wheel'));expect(quickAction).toHaveBeenCalledWith('style','wheel');
  fireEvent.press(view.getByText('Everyone online'));expect(quickAction).toHaveBeenCalledWith('audience','all');
});
test('shared selection blocks with 1 2 3 and then offers creator retry and close',async()=>{
  jest.useFakeTimers();jest.setSystemTime(10000);
  const retry=jest.fn(),close=jest.fn(),visibility=jest.fn();
  const wheel:Wheel={roomName:'Prize draw',names:['Alice','Bob'],winner:'Alice',style:'names',serverOffset:0,round:{id:'quick',memberIds:['a','b'],winnerId:'a',weights:[],startAt:13000,duration:5400,turns:7,landingOffset:0,seed:0}};
  const view=render(<LiveSelectionOverlay wheel={wheel} rtl={false} general expiresAt={60000} onVisibility={visibility} onRetry={retry} onClose={close}/>);
  await act(async()=>{jest.advanceTimersByTime(500);});expect(visibility).toHaveBeenCalledWith(true);expect(view.getByText('1')).toBeTruthy();
  await act(async()=>{jest.advanceTimersByTime(500);});expect(view.getByText('2')).toBeTruthy();
  await act(async()=>{jest.advanceTimersByTime(1000);});expect(view.getByText('3')).toBeTruthy();
  await act(async()=>{jest.advanceTimersByTime(6650);});expect(view.getByText('We have our pick!')).toBeTruthy();
  fireEvent.press(view.getByText('Try again'));expect(retry).toHaveBeenCalledTimes(1);
  fireEvent.press(view.getByText('Close'));expect(close).toHaveBeenCalledTimes(1);
});
