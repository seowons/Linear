# -*- coding: utf-8 -*-
"""
Spyder Editor

This is a temporary script file.
"""

from gtts import gtts
from pydub import AudioSegment
import matplotlib.pyplot as plt
import scipy.fftpack
import scipy.io.wavfile
import numpy as np

# GTTS 라이브러리를 이용하여 음성파일 생성
def test2mp3():
    tts = gtts('리니야', lang='ko')
    tts.save('리니야.mp3')

    # AudioSegment 라이브러리로 음성포멧 조정
    w = AudioSegment.from_mp3('리니야.mp3')
    w.export('리니야.wav', format='wav')    