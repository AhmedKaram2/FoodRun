import { useCallback, useRef, useState } from 'react';

// Dismissing feedback must not erase an error used to show connection recovery or a saved request.
export function useFeedback() {
  const [error, updateError] = useState(''), [notice, updateNotice] = useState('');
  const [feedback, updateFeedback] = useState(null);
  const sequence = useRef(0);
  const show = useCallback((message, isError, source) => {
    if (message) updateFeedback({ id: ++sequence.current, message, isError, source, duration: isError ? 7000 : 4000 });
    else updateFeedback(current => current?.source === source ? null : current);
  }, []);
  const setError = useCallback(message => { updateError(message); show(message, true, 'error'); }, [show]);
  const setNotice = useCallback((message, isError = false) => { updateNotice(message); show(message, isError, 'notice'); }, [show]);
  const dismissFeedback = useCallback(id => updateFeedback(current => current?.id === id ? null : current), []);
  return { error, notice, setError, setNotice, feedback, dismissFeedback };
}
