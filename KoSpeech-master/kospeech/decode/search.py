# -*- coding: utf-8 -*-
# Soohwan Kim, Seyoung Bae, Cheolhwang Won.
# @ArXiv : KoSpeech: Open-Source Toolkit for End-to-End Korean Speech Recognition
# This source code is licensed under the Apache 2.0 License license found in the
# LICENSE file in the root directory of this source tree.

import torch
import torch.nn as nn
import pandas as pd
from queue import Queue
from kospeech.metrics import CharacterErrorRate, WordErrorRate
from kospeech.models.las.topk_decoder import TopKDecoder
from kospeech.models.las.las import ListenAttendSpell
from kospeech.utils import logger


class GreedySearch(object):
    """ Provides greedy search and save result to csv format """
    def __init__(self, vocab, metric: str = 'char'):
        self.target_list = list()
        self.predict_list = list()
        self.vocab = vocab

        if metric == 'char':
            self.metric = CharacterErrorRate(vocab)
        elif metric == 'word':
            self.metric = WordErrorRate(vocab)
        else:
            raise ValueError("Unsupported metric : {0}".format(metric))

    def search(self, model: nn.Module, queue: Queue, device: str, print_every: int) -> float:
        cer = 0
        total_sent_num = 0
        timestep = 0

        model.eval()

        with torch.no_grad():
            while True:
                inputs, targets, input_lengths, target_lengths = queue.get()
                if inputs.shape[0] == 0:
                    break

                inputs = inputs.to(device)
                targets = targets.to(device)

                output = model(inputs, input_lengths, teacher_forcing_ratio=0.0, return_decode_dict=False)
                logit = torch.stack(output, dim=1).to(device)
                pred = logit.max(-1)[1]

                for idx in range(targets.size(0)):
                    self.target_list.append(
                        self.vocab.label_to_string(targets[idx])
                    )
                    self.predict_list.append(
                        self.vocab.label_to_string(pred[idx].cpu().detach().numpy())
                    )

                cer = self.metric(targets[:, 1:], pred)
                total_sent_num += targets.size(0)

                if timestep % print_every == 0:
                    logger.info('cer: {:.2f}'.format(cer))

                timestep += 1

        return cer

    def save_result(self, save_path: str) -> None:
        results = {
            'targets': self.target_list,
            'predictions': self.predict_list
        }
        results = pd.DataFrame(results)
        results.to_csv(save_path, index=False, encoding='cp949')


class BeamSearch(GreedySearch):
    """ Provides beam search decoding. """
    def __init__(self, vocab, k):
        super(BeamSearch, self).__init__(vocab)
        self.k = k

    def search(self, model: ListenAttendSpell, queue: Queue, device: str, print_every: int) -> float:
        if isinstance(model, nn.DataParallel):
            topk_decoder = TopKDecoder(model.module.decoder, self.k)
            model.module.set_decoder(topk_decoder)
        else:
            topk_decoder = TopKDecoder(model.decoder, self.k)
            model.set_decoder(topk_decoder)
        return super(BeamSearch, self).search(model, queue, device, print_every)
