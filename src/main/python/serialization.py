import pickle


def serialize(foo, path):
    with open(path, "wb") as file:
        pickle.dump(foo, file, protocol=pickle.HIGHEST_PROTOCOL)


def deserialize(path):
    with open(path, "rb") as file:
        file.seek(0)
        return pickle.load(file)
