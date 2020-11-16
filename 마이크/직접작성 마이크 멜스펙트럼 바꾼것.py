import pyaudio
import wave
import numpy as np
import librosa

CHUNK = 2**10
RATE = 44100

FORMAT = pyaudio.paInt16
CHANNELS = 1
#RECORD_SECONDS = 10 #마이크 10초 제한
#WAVE_OUTPUT_FILENAME = "output.wav"



p=pyaudio.PyAudio()
stream = p.open(format=pyaudio.paInt16,channels=1,rate=RATE,input=True,
                frames_per_buffer=CHUNK)
print("* recording")

frames = [] #마이크 데이터 저장하는 공간

#for i in range(0, int(RATE /CHUNK * RECORD_SECONDS)): #이거는 마이크10초까지 제한해서 녹음하는것
    #data = stream.read(CHUNK)
    #frames.append(data)

while(True):
    data = np.fromstring(stream.read(CHUNK),dtype=np.int16)
    #print(int(np.average(np.average(np.abs(data))))) 간단하게 평균값의 처리이다.
    #libras 으로 이용해서 멜스펙트럼 바꿔놓자.
    #fft = np.fft.fft(data) / len(data) #주파수신호
    #print(abs(fft))
    S = librosa.feature.melspectrogram(data.astype('float32'), sr=RATE, n_mels = 128,fmax=4000) #멜스펙트럼 바꾼것
    melspec = librosa.core.power_to_db(S,ref=np.max)
    print(melspec)

stream.stop_stream()
stream.close()
p.terminate()


# 마이크데이터를 파일로 저장
#wf = wave.open(WAVE_OUTPUT_FILENAME, 'wb')
#wf.setnchannels(CHANNELS)
#wf.setsampwidth(p.get_sample_size(FORMAT))
#wf.setframerate(RATE)
#wf.writeframes(b''.join(frames))
#wf.close()
