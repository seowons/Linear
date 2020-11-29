# -*- coding: utf-8 -*-
# Soohwan Kim, Seyoung Bae, Cheolhwang Won.
# @ArXiv : KoSpeech: Open-Source Toolkit for End-to-End Korean Speech Recognition
# This source code is licensed under the Apache 2.0 License license found in the
# LICENSE file in the root directory of this source tree.

import os
import re


def bracket_filter(sentence, mode='phonetic'):
    new_sentence = str()

    if mode == 'phonetic':
        flag = False

        for ch in sentence:
            if ch == '(' and flag is False:
                flag = True
                continue
            if ch == '(' and flag is True:
                flag = False
                continue
            if ch != ')' and flag is False:
                new_sentence += ch

    elif mode == 'spelling':
        flag = True

        for ch in sentence:
            if ch == '(':
                continue
            if ch == ')':
                if flag is True:
                    flag = False
                    continue
                else:
                    flag = True
                    continue
            if ch != ')' and flag is True:
                new_sentence += ch

    else:
        raise ValueError("Unsupported mode : {0}".format(mode))

    return new_sentence


def special_filter(sentence, mode='phonetic', replace=None):
    SENTENCE_MARK = ['?', '!', '.']
    NOISE = ['o', 'n', 'u', 'b', 'l']
    EXCEPT = ['/', '+', '*', '-', '@', '$', '^', '&', '[', ']', '=', ':', ';', ',']

    new_sentence = str()
    for idx, ch in enumerate(sentence):
        if ch not in SENTENCE_MARK:
            if idx + 1 < len(sentence) and ch in NOISE and sentence[idx + 1] == '/':
                continue

        if ch == '#':
            new_sentence += '샾'

        elif ch == '%':
            if mode == 'phonetic':
                new_sentence += replace
            elif mode == 'spelling':
                new_sentence += '%'

        elif ch not in EXCEPT:
            new_sentence += ch

    pattern = re.compile(r'\s\s+')
    new_sentence = re.sub(pattern, ' ', new_sentence.strip())
    return new_sentence


def sentence_filter(raw_sentence, mode, replace=None):
    return special_filter(bracket_filter(raw_sentence, mode), mode, replace)


def preprocess(dataset_path, mode='phonetic'):
    print('preprocess started..')

    audio_paths = list()
    transcripts = list()

    percent_files = {
        '087797': '퍼센트',
        '215401': '퍼센트',
        '284574': '퍼센트',
        '397184': '퍼센트',
        '501006': '프로',
        '502173': '프로',
        '542363': '프로',
        '581483': '퍼센트'
    }

    for folder in os.listdir(dataset_path):
        # folder : {KsponSpeech_01, ..., KsponSpeech_05}
        if not folder.startswith('KsponSpeech'):
            continue
        path = os.path.join(dataset_path, folder)
        for idx, subfolder in enumerate(os.listdir(path)):
            path = os.path.join(dataset_path, folder, subfolder)

            for jdx, file in enumerate(os.listdir(path)):
                if file.endswith('.txt'):
                    with open(os.path.join(path, file), "r", encoding='cp949') as f:
                        raw_sentence = f.read()
                        if file[12:18] in percent_files.keys():
                            new_sentence = sentence_filter(raw_sentence, mode, percent_files[file[12:18]])
                        else:
                            new_sentence = sentence_filter(raw_sentence, mode=mode)

                    audio_paths.append(os.path.join(folder, subfolder, file))
                    transcripts.append(new_sentence)

                else:
                    continue

    return audio_paths, transcripts