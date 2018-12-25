# Programming Model

To construct the computation, the basic primitive is `Task`, which is analog to a computation instance, but with more distributed capabilities.

The `Task` instance has following properties:

1. Stateless.
2. Serving like a function with topic-based pub/sub model, with one binding for specific topic.
3. Data coming from different topic served as columnar data for analytics workload.
4. Can be distinguished for two types, `PinTask` and `PhantomTask`, which refer to execution under specific affinity or not.

## Construction

To build a task, you need to define following properties:

* Analytics flow.
* Type of task.
* Operate on topic reference.

It's worth noting that queries in `PhantomTask` can be auto optimized, but `PinTask` is not. 
Therefore, a better practice is to keep `PinTask` lightweight and produce the heavy work in phantom task as possible, unless you've deep understanding underlying execution condition.

```python

import taskit

@taskit.phantom_task
def word_count_task():

    # Source.of construct a channel via topic.
    source = Source.of('text')

    word_count = source
      .flat_map(lambda text: text.split())
      .map(lambda word: (word, 1))
      .reduce_by_key(lambda x, y: x + y)


    graph = word_count.to('word-count')

    return graph


if __name__ == "__main__":
    taskit = Taskit()
    taskit.spawn(word_count_task)
```

To create a `PinTask`:

```python

import pyarrow
import taskit

@taskit.source
def load_text_from_file():
    with open('tmp/data/file.txt') as f:
        text = f.read()
        return taskit.array(text)
        
@taskit.pin_task
def produce_text_task():
    source = load_text_from_file()
    return source.to('text')


if __name__ = "__main__":
    taskit = Taskit()
    taskit.spawn(produce_text_task)

```
