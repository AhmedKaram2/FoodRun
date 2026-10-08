// Queries only. Never resend an order/payment mutation after an ambiguous response.
export function pendingRecovery({id,query,current,complete,schedule=setTimeout,cancel=clearTimeout}) {
  let closed=false,timer;
  const check=async()=>{
    if(closed||!current(id))return;
    try {
      const result=await query(id);
      if(closed||!current(id))return;
      if(result.code!=='COMMAND_UNKNOWN'){complete(result);return;}
    }catch { if(closed||!current(id))return; }
    timer=schedule(check,5000);
  };
  check();
  return ()=>{closed=true;cancel(timer);};
}
