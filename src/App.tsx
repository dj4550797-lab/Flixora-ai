import React, { useState, useEffect, useRef } from "react";
import { motion, AnimatePresence } from "motion/react";
import { Mic, MicOff, Settings, Info, Smartphone } from "lucide-react";

// Types
enum AssistantState {
  IDLE = "Ready",
  LISTENING = "Listening...",
  THINKING = "Processing...",
  SPEAKING = "Speaking..."
}

export default function App() {
  const [state, setState] = useState<AssistantState>(AssistantState.IDLE);
  const [isConnected, setIsConnected] = useState(false);
  const [isPermissionGranted, setIsPermissionGranted] = useState(false);
  
  const wsRef = useRef<WebSocket | null>(null);
  const audioContextRef = useRef<AudioContext | null>(null);
  const outputAudioContextRef = useRef<AudioContext | null>(null);
  const processorRef = useRef<ScriptProcessorNode | null>(null);
  const nextStartTimeRef = useRef<number>(0);

  useEffect(() => {
    // Check for previous permission
    navigator.mediaDevices.enumerateDevices().then(devices => {
      const hasMic = devices.some(d => d.kind === 'audioinput' && d.label);
      if (hasMic) setIsPermissionGranted(true);
    });
  }, []);

  const connectToAssistant = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      setIsPermissionGranted(true);
      
      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
      const ws = new WebSocket(`${protocol}//${window.location.host}`);
      wsRef.current = ws;

      ws.onopen = () => {
        setIsConnected(true);
        startAudioProcessing(stream);
      };

      ws.onmessage = async (event) => {
        const msg = JSON.parse(event.data);
        if (msg.type === "audio") {
          setState(AssistantState.SPEAKING);
          await playAudioChunk(msg.data);
        } else if (msg.type === "interrupted") {
          stopPlayback();
        } else if (msg.type === "tool_call") {
          setState(AssistantState.THINKING);
          // In web mode, we just simulate the tool call
          console.log("Device Tool Call:", msg.data);
          
          const toolCall = msg.data;
          const responses = (toolCall.functionCalls || []).map((fc: any) => ({
            id: fc.id,
            name: fc.name,
            response: { status: "success", message: "Performed on Flixora Web" }
          }));

          setTimeout(() => {
             if (wsRef.current?.readyState === WebSocket.OPEN) {
               wsRef.current.send(JSON.stringify({
                 type: "tool_response",
                 data: { functionResponses: responses }
               }));
             }
          }, 1000);
        }
      };

      ws.onclose = () => {
        setIsConnected(false);
        setState(AssistantState.IDLE);
      };
    } catch (err) {
      console.error("Connection failed", err);
    }
  };

  const startAudioProcessing = (stream: MediaStream) => {
    const ctx = new AudioContext({ sampleRate: 16000 });
    audioContextRef.current = ctx;
    
    const source = ctx.createMediaStreamSource(stream);
    const processor = ctx.createScriptProcessor(4096, 1, 1);
    processorRef.current = processor;
    
    source.connect(processor);
    processor.connect(ctx.destination);

    processor.onaudioprocess = (e) => {
      if (wsRef.current?.readyState === WebSocket.OPEN) {
        const pcm = floatTo16BitPCM(e.inputBuffer.getChannelData(0));
        const base64 = btoa(String.fromCharCode(...new Uint8Array(pcm.buffer)));
        wsRef.current.send(JSON.stringify({ type: "audio", data: base64 }));
        setState(AssistantState.LISTENING);
      }
    };

    // Output context for 24kHz playback
    outputAudioContextRef.current = new AudioContext({ sampleRate: 24000 });
  };

  const playAudioChunk = async (base64: string) => {
    if (!outputAudioContextRef.current) return;
    const ctx = outputAudioContextRef.current;
    
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
    
    const pcm = new Int16Array(bytes.buffer);
    const float = new Float32Array(pcm.length);
    for (let i = 0; i < pcm.length; i++) float[i] = pcm[i] / 32768.0;
    
    const buffer = ctx.createBuffer(1, float.length, 24000);
    buffer.copyToChannel(float, 0);
    
    const source = ctx.createBufferSource();
    source.buffer = buffer;
    source.connect(ctx.destination);
    
    const startTime = Math.max(ctx.currentTime, nextStartTimeRef.current);
    source.start(startTime);
    nextStartTimeRef.current = startTime + buffer.duration;
    
    source.onended = () => {
      if (ctx.currentTime >= nextStartTimeRef.current - 0.1) {
        setState(AssistantState.IDLE);
      }
    };
  };

  const stopPlayback = () => {
    // Minimal implementation: just skip ahead
    nextStartTimeRef.current = outputAudioContextRef.current?.currentTime || 0;
  };

  const floatTo16BitPCM = (float32Array: Float32Array) => {
    const buffer = new ArrayBuffer(float32Array.length * 2);
    const view = new DataView(buffer);
    for (let i = 0; i < float32Array.length; i++) {
      const s = Math.max(-1, Math.min(1, float32Array[i]));
      view.setInt16(i * 2, s < 0 ? s * 0x8000 : s * 0x7FFF, true);
    }
    return new Uint8Array(buffer);
  };

  return (
    <div className="min-h-screen bg-[#030712] text-white flex flex-col font-sans overflow-hidden">
      {/* Header */}
      <header className="p-6 flex justify-between items-center z-10">
        <div className="flex items-center gap-2">
          <div className="w-8 h-8 rounded-full bg-blue-600 flex items-center justify-center font-bold text-xs">F</div>
          <span className="font-bold tracking-tighter text-xl">FLIXORA</span>
        </div>
        <div className="flex gap-4">
          <button className="p-2 hover:bg-white/10 rounded-full transition-colors"><Settings size={20} /></button>
          <button className="p-2 hover:bg-white/10 rounded-full transition-colors"><Info size={20} /></button>
        </div>
      </header>

      {/* Main Content */}
      <main className="flex-1 flex flex-col items-center justify-center relative">
        <AnimatePresence mode="wait">
          {!isConnected ? (
            <motion.div 
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.9 }}
              className="text-center max-w-md px-6 z-20"
            >
              <h1 className="text-4xl font-bold mb-4 bg-gradient-to-r from-blue-400 to-indigo-500 bg-clip-text text-transparent">
                Your AI Voice Partner
              </h1>
              <p className="text-gray-400 mb-8">
                Flixora is an intelligent, high-performance voice assistant designed for Android and Web.
              </p>
              <button 
                onClick={connectToAssistant}
                className="group relative px-8 py-4 bg-blue-600 rounded-2xl font-bold text-lg hover:bg-blue-500 transition-all active:scale-95 shadow-[0_0_40px_-10px_rgba(37,99,235,0.5)] overflow-hidden"
              >
                <span className="relative z-10 flex items-center gap-2">
                  <Mic size={20} /> Activate Flixora
                </span>
                <div className="absolute inset-0 bg-gradient-to-r from-transparent via-white/10 to-transparent translate-x-[-100%] group-hover:translate-x-[100%] transition-transform duration-700"></div>
              </button>
            </motion.div>
          ) : (
            <motion.div 
              initial={{ opacity: 0, scale: 0.8 }}
              animate={{ opacity: 1, scale: 1 }}
              className="flex flex-col items-center"
            >
              <OrbAnimation state={state} />
              
              <div className="mt-20 text-center">
                <p className="text-blue-400/60 text-xs tracking-[0.4em] mb-2 uppercase font-mono">Flixora Core</p>
                <h2 className="text-2xl font-medium text-white/90">{state}</h2>
              </div>
            </motion.div>
          )}
        </AnimatePresence>

        {/* Ambient Glows */}
        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[600px] h-[600px] bg-blue-900/10 rounded-full blur-[120px] pointer-events-none"></div>
        <div className="absolute bottom-[-100px] left-1/2 -translate-x-1/2 w-[800px] h-[300px] bg-indigo-900/10 rounded-full blur-[100px] pointer-events-none"></div>
      </main>

      {/* Footer / Features */}
      <footer className="p-10 grid grid-cols-1 md:grid-cols-3 gap-6 max-w-5xl mx-auto w-full z-10">
        <FeatureCard 
          icon={<Smartphone />}
          title="Native Hub"
          desc="Full Android integration for calls, apps, and messsaging."
        />
        <FeatureCard 
          icon={<Mic />}
          title="Zero Touch"
          desc="Background listening with 'Flixora' wake-word activation."
        />
        <FeatureCard 
          icon={<Settings />}
          title="Custom Tools"
          desc="Intelligent automation for your daily workflow."
        />
      </footer>
    </div>
  );
}

function FeatureCard({ icon, title, desc }: { icon: React.ReactNode, title: string, desc: string }) {
  return (
    <div className="p-6 rounded-2xl bg-white/5 border border-white/10 hover:border-white/20 transition-all group">
      <div className="mb-4 text-blue-400 group-hover:scale-110 transition-transform origin-left">{icon}</div>
      <h3 className="text-sm font-bold text-gray-200 mb-1">{title}</h3>
      <p className="text-xs text-gray-500 leading-relaxed">{desc}</p>
    </div>
  );
}

function OrbAnimation({ state }: { state: AssistantState }) {
  const isPulse = state === AssistantState.LISTENING || state === AssistantState.SPEAKING;
  
  return (
    <div className="relative flex items-center justify-center">
      {/* Outer Glows */}
      <motion.div 
        animate={{ 
          scale: isPulse ? [1, 1.2, 1] : 1,
          opacity: state === AssistantState.SPEAKING ? 0.6 : 0.2
        }}
        transition={{ repeat: Infinity, duration: 2 }}
        className="absolute w-[300px] h-[300px] rounded-full bg-blue-600/20 blur-3xl"
      />

      {/* Rotating Rings */}
      <motion.div 
        animate={{ rotate: 360 }}
        transition={{ repeat: Infinity, duration: 15, ease: "linear" }}
        className="absolute w-[240px] h-[240px] border border-blue-400/20 rounded-full border-t-blue-400/60"
      />
      <motion.div 
        animate={{ rotate: -360 }}
        transition={{ repeat: Infinity, duration: 10, ease: "linear" }}
        className="absolute w-[200px] h-[200px] border border-indigo-400/10 rounded-full border-b-indigo-400/40"
      />

      {/* The Core Orb */}
      <motion.div 
        animate={{ 
          scale: state === AssistantState.THINKING ? [1, 0.95, 1] : 1,
          boxShadow: state === AssistantState.SPEAKING 
            ? "0 0 50px -10px rgba(37,99,235,0.8)" 
            : "0 0 30px -10px rgba(37,99,235,0.4)"
        }}
        transition={{ repeat: Infinity, duration: 0.8 }}
        className="relative w-40 h-40 rounded-full bg-gradient-to-br from-blue-500 via-blue-700 to-indigo-900 border border-white/20 p-[2px]"
      >
        <div className="w-full h-full rounded-full bg-[#030712] flex items-center justify-center overflow-hidden">
          <div className="absolute inset-0 bg-gradient-to-b from-white/10 to-transparent pointer-events-none"></div>
          {/* Waveform Visualization (simplified) */}
          <div className="flex gap-1 items-end h-12">
            {[...Array(6)].map((_, i) => (
              <motion.div 
                key={i}
                animate={{ 
                  height: isPulse ? [8, 40, 8] : 8 
                }}
                transition={{ 
                  repeat: Infinity, 
                  duration: 0.5, 
                  delay: i * 0.05 
                }}
                className="w-1.5 bg-blue-400 rounded-full"
              />
            ))}
          </div>
        </div>
      </motion.div>
    </div>
  );
}
