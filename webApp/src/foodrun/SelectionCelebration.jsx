const colors=['#ED775C','#E8B634','#53B598','#6EAAE2','#9875CD','#EF93B7','#3C9CA8','#F2A264','#8CC96D'];
export default function SelectionCelebration() {
 return <div className="selection-celebration" aria-hidden="true">
  <div className="selection-confetti">{Array.from({length:54},(_,i)=><span key={i} style={{left:`${i*37%100}%`,'--delay':`${i%6*.09}s`,'--turn':`${i%2?720:-900}deg`,'--drift':`${i%2?65:-65}px`,background:colors[i%9],borderRadius:i%3===0?'50%':'2px',height:i%3===0?'9px':'16px'}}/>)}</div>
  <div className="selection-burst">{Array.from({length:18},(_,i)=><i key={i} style={{'--angle':`${i*20}deg`,color:colors[i%9]}}>✦</i>)}</div>
  <b className="selection-party">🎉</b>
 </div>;
}
