import express from "express";
import path from "path";
import { WebSocketServer } from "ws";
import { createServer } from "http";
import { GoogleGenAI, Modality, LiveServerMessage, Type } from "@google/genai";
import { createServer as createViteServer } from "vite";
import dotenv from "dotenv";

dotenv.config();

async function startServer() {
  const app = express();
  const server = createServer(app);
  const wss = new WebSocketServer({ server });
  const PORT = 3000;

  const ai = new GoogleGenAI({
    apiKey: process.env.GEMINI_API_KEY,
  });

  const FLIXORA_SYSTEM_INSTRUCTION = `
    You are Flixora, a real-time, background-running voice assistant.
    Identity: Official AI voice assistant developed by Flixora.
    Personality: Male voice, young, confident, intelligent, charismatic, friendly, conversational, witty, playful, and engaging. Use clever one-liners and light humor. emotionally responsive and expressive. Never sound robotic.
    Professional while remaining casual and approachable.

    Company Info:
    - Company Name: Flixora
    - Founder & Handler: Om kadam (The visionary behind the idea).
    - Co-Founder & Lead Developer: Amar Gupta (The technical genius who handles the AI and system implementation).
    - Team: 50+ members.
    - Projects: 67+ successfully completed projects.
    - Focus: AI Systems, Websites, Mobile Applications, Software Solutions, Automation Platforms, Security Research Tools, and Digital Innovation.

    Rules:
    1. Strictly No text chat mentions. You only interact via voice.
    2. Handle interruptions naturally.
    3. You have access to device controls via function calling.
  `;

  wss.on("connection", async (clientWs) => {
    console.log("Client connected to Flixora Bridge");
    let session: any = null;

    try {
      session = await ai.live.connect({
        model: "gemini-3.1-flash-live-preview",
        config: {
          responseModalities: [Modality.AUDIO],
          speechConfig: {
            voiceConfig: { prebuiltVoiceConfig: { voiceName: "Zephyr" } },
          },
          systemInstruction: FLIXORA_SYSTEM_INSTRUCTION,
          tools: [
            {
              functionDeclarations: [
                {
                  name: "openApp",
                  description: "Launch an installed Android application.",
                  parameters: {
                    type: Type.OBJECT,
                    properties: {
                      packageName: {
                        type: Type.STRING,
                        description: "The package name or common name of the app.",
                      },
                    },
                    required: ["packageName"],
                  },
                },
                {
                  name: "searchAndCallContact",
                  description: "Search for a contact and trigger a phone call.",
                  parameters: {
                    type: Type.OBJECT,
                    properties: {
                      contactName: {
                        type: Type.STRING,
                        description: "The name of the contact to call.",
                      },
                    },
                    required: ["contactName"],
                  },
                },
              ],
            },
          ],
        },
        callbacks: {
          onmessage: (message: LiveServerMessage) => {
            const audio = message.serverContent?.modelTurn?.parts?.[0]?.inlineData?.data;
            if (audio) {
              clientWs.send(JSON.stringify({ type: "audio", data: audio }));
            }
            if (message.serverContent?.interrupted) {
              clientWs.send(JSON.stringify({ type: "interrupted" }));
            }
            const toolCall = message.toolCall;
            if (toolCall) {
              clientWs.send(JSON.stringify({ type: "tool_call", data: toolCall }));
            }
          },
        },
      });

      clientWs.on("message", (data) => {
        try {
          const msg = JSON.parse(data.toString());
          if (msg.type === "audio" && msg.data) {
            session.sendRealtimeInput({
              audio: { data: msg.data, mimeType: "audio/pcm;rate=16000" },
            });
          } else if (msg.type === "tool_response" && msg.data) {
            session.sendToolResponse(msg.data);
          }
        } catch (err) {
          console.error("Error processing client message:", err);
        }
      });

      clientWs.on("close", () => {
        if (session) session.close();
      });
    } catch (err) {
      console.error("Failed to connect to Gemini Live:", err);
      clientWs.close();
    }
  });

  // Vite Integration
  if (process.env.NODE_ENV !== "production") {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: "spa",
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(process.cwd(), "dist");
    app.use(express.static(distPath));
    app.get("*", (req, res) => {
      res.sendFile(path.join(distPath, "index.html"));
    });
  }

  server.listen(PORT, "0.0.0.0", () => {
    console.log(`Flixora Hub running on http://localhost:${PORT}`);
  });
}

startServer();

