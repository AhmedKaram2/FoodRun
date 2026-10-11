const colors=['#C44730','#245C49','#645082'];
export default function SelectionCountdown({number,ready,subtitle}) {
 return <div className="selection-countdown">
  <p>{ready}</p>
  <div className="countdown-arena" key={number} style={{'--count-tone':colors[number-1]}}>
   <i className="countdown-halo" aria-hidden="true"/><i className="countdown-ring" aria-hidden="true"/><i className="countdown-ring outer" aria-hidden="true"/>
   <div className="countdown-orbit" aria-hidden="true">{Array.from({length:9},(_,i)=><i key={i} style={{'--angle':`${i*40}deg`,color:['#ED775C','#E8B634','#53B598','#6EAAE2','#9875CD','#EF93B7','#3C9CA8','#F2A264','#8CC96D'][i]}}>✦</i>)}</div>
   <strong aria-live="assertive">{number}</strong>
  </div>
  {subtitle&&<p>{subtitle}</p>}
 </div>;
}
