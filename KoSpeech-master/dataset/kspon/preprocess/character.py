# -*- coding: utf-8 -*-
# Soohwan Kim, Seyoung Bae, Cheolhwang Won.
# @ArXiv : KoSpeech: Open-Source Toolkit for End-to-End Korean Speech Recognition
# This source code is licensed under the Apache 2.0 License license found in the
# LICENSE file in the root directory of this source tree.

import os
import pandas as pd


def load_label(filepath):
    char2id = dict()
    id2char = dict()

    ch_labels = pd.read_csv(filepath, encoding="utf-8")

    id_list = ch_labels["id"]
    char_list = ch_labels["char"]
    freq_list = ch_labels["freq"]

    for (id_, char, freq) in zip(id_list, char_list, freq_list):
        char2id[char] = id_
        id2char[id_] = char
    return char2id, id2char


def sentence_to_target(sentence, char2id):
    target = str()

    for ch in sentence:
        target += (str(char2id[ch]) + ' ')

    return target[:-1]


def generate_character_labels(transcripts, labels_dest):
    print('create_char_labels started..')

    label_list = list()
    label_freq = list()

    for transcript in transcripts:
        for ch in transcript:
            if ch not in label_list:
                label_list.append(ch)
                label_freq.append(1)
            else:
                label_freq[label_list.index(ch)] += 1

    # sort together Using zip
    label_freq, label_list = zip(*sorted(zip(label_freq, label_list), reverse=True))
    label = {'id': [0, 1, 2], 'char': ['<pad>', '<sos>', '<eos>'], 'freq': [0, 0, 0]}

    for idx, (ch, freq) in enumerate(zip(label_list, label_freq)):
        label['id'].append(idx + 3)
        label['char'].append(ch)
        label['freq'].append(freq)

    # save to csv
    label_df = pd.DataFrame(label)
    label_df.to_csv(os.path.join(labels_dest, "aihub_vocabs.csv"), encoding="utf-8", index=False)


def generate_character_script(audio_paths, transcripts, labels_dest):
    print('create_script started..')
    char2id, id2char = load_label(os.path.join(labels_dest, "aihub_vocabs.csv"))

    with open(os.path.join("../../data/transcripts.txt"), "w") as trans_file:
        for audio_path, transcript in zip(audio_paths, transcripts):
            number_transcript = sentence_to_target(transcript, char2id)
            audio_path = audio_path.replace('txt', 'pcm')
            line = "%s\t%s\t%s\n" % (audio_path, transcript, number_transcript)
            trans_file.write(line)
